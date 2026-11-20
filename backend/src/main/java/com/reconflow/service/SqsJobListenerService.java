package com.reconflow.service;

import com.reconflow.entity.UploadedFile;
import com.reconflow.repository.UploadedFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SqsJobListenerService {

    private final SqsClient sqsClient;
    private final UploadedFileRepository uploadedFileRepository;
    private final com.reconflow.repository.ReconciliationRunRepository reconciliationRunRepository;
    private final JobLauncher jobLauncher;
    private final Job gatewayReconciliationJob;
    private final Job bankReconciliationJob;
    private final StringRedisTemplate redisTemplate;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Value("${aws.sqs.queue-url}")
    private String queueUrl;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${scheduling.enabled:true}")
    private boolean schedulingEnabled;

    /**
     * Must comfortably exceed the longest expected batch job. A short lock TTL would
     * let an SQS redelivery acquire the lock while the original worker is still
     * running, which would cause the same file to be processed twice.
     */
    @Value("${reconflow.processing-lock-ttl-seconds:1800}")
    private long processingLockTtlSeconds = 1800;

    @Scheduled(fixedDelay = 10000) // Poll every 10 seconds
    public void scheduledPoll() {
        if (!schedulingEnabled) return;
        pollMainQueue();
    }

    public void pollMainQueue() {
        try {
            ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(5)
                    .waitTimeSeconds(1) // Reduced for faster E2E testing
                    .build();

            List<Message> messages = sqsClient.receiveMessage(receiveRequest).messages();

            for (Message message : messages) {
                log.info("Received message from SQS: {}", message.body());

                try {
                    com.fasterxml.jackson.databind.JsonNode json = objectMapper.readTree(message.body());
                    String fileIdStr = json.has("fileId") ? json.get("fileId").asText() : message.body().trim();
                    UUID fileId = UUID.fromString(fileIdStr);

                    UploadedFile uploadedFile = uploadedFileRepository.findById(fileId).orElse(null);

                    boolean processable = uploadedFile != null
                            && (uploadedFile.getStatus() == com.reconflow.entity.status.UploadedFileStatus.QUEUED
                                || uploadedFile.getStatus() == com.reconflow.entity.status.UploadedFileStatus.PROCESSING);

                    if (processable) {
                        // Distributed lock via Redis SETNX to prevent duplicate processing
                        String lockKey = "recon:lock:file:" + fileId;
                        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                                lockKey, "true", java.time.Duration.ofSeconds(processingLockTtlSeconds));

                        if (!Boolean.TRUE.equals(acquired)) {
                            log.info("File {} already being processed by another consumer. Skipping.", fileId);
                            continue;
                        }

                        try {
                            // Re-read under the lock: the instance read above may be stale, and a
                            // PROCESSING row means a prior worker died before deleting its message.
                            UploadedFile locked = uploadedFileRepository.findById(fileId).orElse(null);
                            if (locked == null) {
                                sqsClient.deleteMessage(DeleteMessageRequest.builder()
                                        .queueUrl(queueUrl)
                                        .receiptHandle(message.receiptHandle())
                                        .build());
                                continue;
                            }

                            if (locked.getStatus() == com.reconflow.entity.status.UploadedFileStatus.PROCESSING) {
                                locked.transitionTo(com.reconflow.entity.status.UploadedFileStatus.QUEUED);
                                uploadedFileRepository.save(locked);
                                log.warn("Recovered abandoned PROCESSING file {} back to QUEUED", fileId);
                            }

                            if (locked.getStatus() == com.reconflow.entity.status.UploadedFileStatus.QUEUED) {
                                processFileAfterLock(json, locked, fileId, message);
                            } else {
                                // Another consumer finished it while we waited for the lock.
                                sqsClient.deleteMessage(DeleteMessageRequest.builder()
                                        .queueUrl(queueUrl)
                                        .receiptHandle(message.receiptHandle())
                                        .build());
                            }
                        } finally {
                            try {
                                redisTemplate.delete(lockKey);
                            } catch (Exception e) {
                                log.warn("Failed to release lock for file {}", fileId, e);
                            }
                        }
                    } else {
                        log.warn("File {} not found or in a non-processable status ({})", fileId,
                                uploadedFile != null ? uploadedFile.getStatus() : "missing");
                        sqsClient.deleteMessage(DeleteMessageRequest.builder()
                                .queueUrl(queueUrl)
                                .receiptHandle(message.receiptHandle())
                                .build());
                    }
                } catch (Exception ex) {
                    log.error("Failed to process message: {}", message.body(), ex);
                    // Do not delete; let it eventually go to DLQ
                }
            }
        } catch (Exception e) {
            log.error("Failed to poll main SQS queue", e);
        }
    }

    private void processFileAfterLock(com.fasterxml.jackson.databind.JsonNode json, UploadedFile uploadedFile, UUID fileId, Message message) {
        try {
            log.info("Starting Batch Job for File ID: {}, Source: {}", fileId, uploadedFile.getSourceType());

            // Determine Job based on SourceType
            Job jobToRun = "BANK".equalsIgnoreCase(uploadedFile.getSourceType()) ? bankReconciliationJob : gatewayReconciliationJob;

            if ("BANK".equalsIgnoreCase(uploadedFile.getSourceType())) {
                boolean gatewayCompleted = uploadedFileRepository.existsBySourceTypeAndSettlementDateAndStatus(
                        "GATEWAY", uploadedFile.getSettlementDate(), com.reconflow.entity.status.UploadedFileStatus.COMPLETED
                );
                if (!gatewayCompleted) {
                    log.info("Delaying BANK file {} processing. GATEWAY file missing or not COMPLETED for date {}", fileId, uploadedFile.getSettlementDate());
                    uploadedFile.transitionTo(com.reconflow.entity.status.UploadedFileStatus.PENDING_DEPENDENCY);
                    uploadedFileRepository.save(uploadedFile);
                    try {
                        sqsClient.deleteMessage(DeleteMessageRequest.builder()
                                .queueUrl(queueUrl)
                                .receiptHandle(message.receiptHandle())
                                .build());
                    } catch (Exception e) {
                        log.warn("Failed to delete delayed message from SQS: {}", e.getMessage());
                    }
                    return;
                }
            } else if ("GATEWAY".equalsIgnoreCase(uploadedFile.getSourceType())) {
                boolean ledgerCompleted = uploadedFileRepository.existsBySourceTypeAndSettlementDateAndStatus(
                        "LEDGER", uploadedFile.getSettlementDate(), com.reconflow.entity.status.UploadedFileStatus.COMPLETED
                );
                if (!ledgerCompleted) {
                    log.info("Delaying GATEWAY file {} processing. LEDGER file missing for date {}", fileId, uploadedFile.getSettlementDate());
                    uploadedFile.transitionTo(com.reconflow.entity.status.UploadedFileStatus.PENDING_DEPENDENCY);
                    uploadedFileRepository.save(uploadedFile);
                    try {
                        sqsClient.deleteMessage(DeleteMessageRequest.builder()
                                .queueUrl(queueUrl)
                                .receiptHandle(message.receiptHandle())
                                .build());
                    } catch (Exception e) {
                        log.warn("Failed to delete delayed message from SQS: {}", e.getMessage());
                    }
                    return;
                }
            }

            // Dependencies satisfied: now it is safe to move the file into PROCESSING.
            uploadedFile.transitionTo(com.reconflow.entity.status.UploadedFileStatus.PROCESSING);
            uploadedFileRepository.save(uploadedFile);

            java.time.LocalDate today = java.time.LocalDate.now();
            com.reconflow.entity.ReconciliationRun run = getOrCreateRun(today);
            String runIdStr = run.getId().toString();

            JobParameters params = new JobParametersBuilder()
                    .addString("runId", runIdStr)
                    .addString("uploadedFileId", uploadedFile.getId().toString())
                    .addString("minAmount", json.has("minAmount") ? json.get("minAmount").asText() : "0.01")
                    .addString("maxAmount", json.has("maxAmount") ? json.get("maxAmount").asText() : "10000000")
                    .addLong("time", System.currentTimeMillis())
                    .toJobParameters();

            org.springframework.batch.core.JobExecution execution = jobLauncher.run(jobToRun, params);

            if (execution.getStatus() == org.springframework.batch.core.BatchStatus.COMPLETED) {
                sqsClient.deleteMessage(DeleteMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .receiptHandle(message.receiptHandle())
                        .build());
            } else {
                // Compensation: leave the message on the queue so SQS redelivers it
                // (up to the redrive limit) and return the file to QUEUED.
                log.error("Job {} finished with status {} for file {}; leaving message for retry",
                        jobToRun.getName(), execution.getStatus(), fileId);
                compensateToQueued(fileId);
            }
        } catch (Exception ex) {
            log.error("Failed to process file {} in job: {}", fileId, ex.getMessage(), ex);
            compensateToQueued(fileId);
            throw new RuntimeException("Failed to process file " + fileId, ex);
        }
    }

    /**
     * A daily run is shared by every job processed that day. The unique constraint on
     * run_date makes concurrent first-job races safe: the loser re-reads the winner's
     * row instead of creating a second RUNNING run.
     */
    private com.reconflow.entity.ReconciliationRun getOrCreateRun(java.time.LocalDate runDate) {
        return reconciliationRunRepository.findFirstByRunDateOrderByStartedAtAsc(runDate)
                .orElseGet(() -> {
                    try {
                        return reconciliationRunRepository.saveAndFlush(
                                com.reconflow.entity.ReconciliationRun.builder()
                                        .runDate(runDate)
                                        .status(com.reconflow.entity.status.ReconciliationRunStatus.RUNNING)
                                        .build());
                    } catch (org.springframework.dao.DataIntegrityViolationException e) {
                        return reconciliationRunRepository.findFirstByRunDateOrderByStartedAtAsc(runDate)
                                .orElseThrow(() -> e);
                    }
                });
    }

    /**
     * Returns a file to QUEUED after a failed or abandoned processing attempt so that
     * SQS redelivery can retry it. Terminal rows (COMPLETED/FAILED) are left untouched.
     * When retries are exhausted the message lands in the DLQ, where it is recorded as
     * an operational exception instead of being silently discarded.
     */
    private void compensateToQueued(UUID fileId) {
        try {
            uploadedFileRepository.findById(fileId).ifPresent(file -> {
                if (file.getStatus() == com.reconflow.entity.status.UploadedFileStatus.PROCESSING) {
                    file.transitionTo(com.reconflow.entity.status.UploadedFileStatus.QUEUED);
                    uploadedFileRepository.save(file);
                    log.warn("Compensated file {} back to QUEUED for retry", fileId);
                }
            });
        } catch (Exception e) {
            log.error("Failed to compensate file {} back to QUEUED", fileId, e);
        }
    }
}
