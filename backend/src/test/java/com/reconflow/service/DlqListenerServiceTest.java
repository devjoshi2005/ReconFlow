package com.reconflow.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DlqListenerServiceTest {

    @Mock
    private SqsClient sqsClient;

    @Mock
    private DlqFailureService dlqFailureService;

    private DlqListenerService listener;

    private static final String DLQ_URL = "http://localhost:4566/000000000000/reconflow-test-dlq";

    @BeforeEach
    void setUp() {
        listener = new DlqListenerService(sqsClient, dlqFailureService);
        ReflectionTestUtils.setField(listener, "dlqUrl", DLQ_URL);
    }

    @Test
    void persistsFailureBeforeDeletingMessage() {
        String body = "{\"fileId\":\"11111111-1111-1111-1111-111111111111\"}";
        Message message = Message.builder().body(body).receiptHandle("rh-1").build();
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(List.of(message)).build());
        when(dlqFailureService.recordFailure(body)).thenReturn(true);

        listener.pollDeadLetterQueue();

        InOrder inOrder = inOrder(dlqFailureService, sqsClient);
        inOrder.verify(dlqFailureService).recordFailure(body);
        inOrder.verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void leavesMessageInDlqWhenPersistenceFails() {
        String body = "{\"fileId\":\"22222222-2222-2222-2222-222222222222\"}";
        Message message = Message.builder().body(body).receiptHandle("rh-2").build();
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(List.of(message)).build());
        when(dlqFailureService.recordFailure(body)).thenThrow(new RuntimeException("database unavailable"));

        // The listener logs and continues; the unpersisted message must remain in the DLQ.
        listener.pollDeadLetterQueue();

        verify(dlqFailureService).recordFailure(body);
        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
    }
}
