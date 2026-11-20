package com.reconflow.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DlqListenerService {

    private final SqsClient sqsClient;
    private final DlqFailureService dlqFailureService;

    @Value("${aws.sqs.dlq-url}")
    private String dlqUrl;

    @Scheduled(fixedDelay = 60000) // Poll DLQ every minute
    public void pollDeadLetterQueue() {
        try {
            ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                    .queueUrl(dlqUrl)
                    .maxNumberOfMessages(10)
                    .waitTimeSeconds(5) // Short polling for DLQ
                    .build();

            List<Message> messages = sqsClient.receiveMessage(receiveRequest).messages();

            for (Message message : messages) {
                log.error("ALERT: Received failed message from DLQ: {}", message.body());
                try {
                    // Persist before deleting: if recording fails, the message stays in
                    // the DLQ and is retried rather than being silently lost.
                    dlqFailureService.recordFailure(message.body());
                    sqsClient.deleteMessage(DeleteMessageRequest.builder()
                            .queueUrl(dlqUrl)
                            .receiptHandle(message.receiptHandle())
                            .build());
                } catch (Exception e) {
                    log.error("Failed to record DLQ message; leaving it in the DLQ for retry", e);
                }
            }
        } catch (Exception e) {
            log.error("Failed to poll DLQ", e);
        }
    }
}
