package com.reconflow.batch;

import com.reconflow.entity.UploadedFile;
import com.reconflow.entity.status.UploadedFileStatus;
import com.reconflow.repository.UploadedFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * Recovers files that were committed as QUEUED but never made it onto SQS (for
 * example because the best-effort send after commit failed). Re-sending the message
 * is safe: the per-file Redis lock and the status check make duplicate messages
 * no-ops.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueuedFileRecoveryTask {

    private final UploadedFileRepository uploadedFileRepository;
    private final SqsClient sqsClient;

    @Value("${aws.sqs.queue-url}")
    private String queueUrl;

    @Value("${reconflow.queue-recovery.orphaned-after-minutes:5}")
    private long orphanedAfterMinutes;

    @Scheduled(fixedDelayString = "${reconflow.queue-recovery.fixed-delay:120000}")
    public void requeueOrphanedQueuedFiles() {
        ZonedDateTime cutoff = ZonedDateTime.now().minusMinutes(orphanedAfterMinutes);
        List<UploadedFile> orphaned = uploadedFileRepository.findByStatusAndUpdatedAtBefore(
                UploadedFileStatus.QUEUED, cutoff);

        if (orphaned.isEmpty()) {
            return;
        }

        log.warn("Recovery sweeper found {} QUEUED files older than {} minutes",
                orphaned.size(), orphanedAfterMinutes);

        for (UploadedFile file : orphaned) {
            try {
                sqsClient.sendMessage(SendMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .messageBody("{\"fileId\":\"" + file.getId() + "\"}")
                        .build());
                log.warn("Re-enqueued orphaned QUEUED file {}", file.getId());
            } catch (Exception e) {
                log.error("Failed to re-enqueue orphaned QUEUED file {}", file.getId(), e);
            }
        }
    }
}
