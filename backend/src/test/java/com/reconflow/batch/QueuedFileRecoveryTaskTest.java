package com.reconflow.batch;

import com.reconflow.entity.UploadedFile;
import com.reconflow.entity.status.UploadedFileStatus;
import com.reconflow.repository.UploadedFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueuedFileRecoveryTaskTest {

    @Mock
    private UploadedFileRepository uploadedFileRepository;

    @Mock
    private SqsClient sqsClient;

    private QueuedFileRecoveryTask task;

    @BeforeEach
    void setUp() {
        task = new QueuedFileRecoveryTask(uploadedFileRepository, sqsClient);
        ReflectionTestUtils.setField(task, "queueUrl", "http://localhost:4566/000000000000/reconflow-test-queue");
        ReflectionTestUtils.setField(task, "orphanedAfterMinutes", 5L);
    }

    @Test
    void reEnqueuesEveryOrphanedQueuedFile() {
        UploadedFile first = queuedFile();
        UploadedFile second = queuedFile();
        when(uploadedFileRepository.findByStatusAndUpdatedAtBefore(eq(UploadedFileStatus.QUEUED), any(ZonedDateTime.class)))
                .thenReturn(List.of(first, second));

        task.requeueOrphanedQueuedFiles();

        ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqsClient, times(2)).sendMessage(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(SendMessageRequest::messageBody)
                .anySatisfy(body -> assertThat(body).contains(first.getId().toString()))
                .anySatisfy(body -> assertThat(body).contains(second.getId().toString()));
    }

    @Test
    void continuesRecoveringWhenOneSendFails() {
        UploadedFile failing = queuedFile();
        UploadedFile succeeding = queuedFile();
        when(uploadedFileRepository.findByStatusAndUpdatedAtBefore(eq(UploadedFileStatus.QUEUED), any(ZonedDateTime.class)))
                .thenReturn(List.of(failing, succeeding));
        when(sqsClient.sendMessage(any(SendMessageRequest.class)))
                .thenThrow(new RuntimeException("SQS unavailable"))
                .thenReturn(null);

        task.requeueOrphanedQueuedFiles();

        verify(sqsClient, times(2)).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void doesNothingWhenThereAreNoOrphans() {
        when(uploadedFileRepository.findByStatusAndUpdatedAtBefore(eq(UploadedFileStatus.QUEUED), any(ZonedDateTime.class)))
                .thenReturn(List.of());

        task.requeueOrphanedQueuedFiles();

        verify(sqsClient, org.mockito.Mockito.never()).sendMessage(any(SendMessageRequest.class));
    }

    private static UploadedFile queuedFile() {
        return UploadedFile.builder()
                .id(UUID.randomUUID())
                .status(UploadedFileStatus.QUEUED)
                .build();
    }
}
