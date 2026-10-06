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
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;

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
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Value("${aws.sqs.queue-url}")
    private String queueUrl;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${scheduling.enabled:true}")
    private boolean schedulingEnabled;

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
                    .waitTimeSeconds(5) // Long polling
                    .build();
            
            List<Message> messages = sqsClient.receiveMessage(receiveRequest).messages();
            
            for (Message message : messages) {
                log.info("Received message from SQS: {}", message.body());
                
                try {
                    com.fasterxml.jackson.databind.JsonNode json = objectMapper.readTree(message.body());
                    String fileIdStr = json.has("fileId") ? json.get("fileId").asText() : message.body().trim();
                    UUID fileId = UUID.fromString(fileIdStr);
                    
                    UploadedFile uploadedFile = uploadedFileRepository.findById(fileId).orElse(null);
                    
                    if (uploadedFile != null && "QUEUED".equals(uploadedFile.getStatus())) {
                        log.info("Starting Batch Job for File ID: {}, Source: {}", fileId, uploadedFile.getSourceType());
                        
                        // Mark as processing
                        uploadedFile.setStatus("PROCESSING");
                        uploadedFileRepository.save(uploadedFile);

                        // Determine Job based on SourceType
                        Job jobToRun = "BANK".equalsIgnoreCase(uploadedFile.getSourceType()) ? bankReconciliationJob : gatewayReconciliationJob;
                        
                        if ("BANK".equalsIgnoreCase(uploadedFile.getSourceType())) {
                            boolean gatewayCompleted = uploadedFileRepository.existsBySourceTypeAndSettlementDateAndStatus(
                                    "GATEWAY", uploadedFile.getSettlementDate(), "COMPLETED"
                            );
                            if (!gatewayCompleted) {
                                log.info("Delaying BANK file {} processing. GATEWAY file missing or not COMPLETED for date {}", fileId, uploadedFile.getSettlementDate());
                                uploadedFile.setStatus("PENDING_DEPENDENCY");
                                uploadedFileRepository.save(uploadedFile);
                                try {
                                    sqsClient.deleteMessage(DeleteMessageRequest.builder()
                                            .queueUrl(queueUrl)
                                            .receiptHandle(message.receiptHandle())
                                            .build());
                                } catch (Exception e) {
                                    log.warn("Failed to delete delayed message from SQS: {}", e.getMessage());
                                }
                                continue;
                            }
                        } else if ("GATEWAY".equalsIgnoreCase(uploadedFile.getSourceType())) {
                            boolean ledgerCompleted = uploadedFileRepository.existsBySourceTypeAndGatewayAndSettlementDateAndStatus(
                                    "LEDGER", uploadedFile.getGateway(), uploadedFile.getSettlementDate(), "COMPLETED"
                            );
                            if (!ledgerCompleted) {
                                log.info("Delaying GATEWAY file {} processing. LEDGER file missing for date {}", fileId, uploadedFile.getSettlementDate());
                                uploadedFile.setStatus("PENDING_DEPENDENCY");
                                uploadedFileRepository.save(uploadedFile);
                                try {
                                    sqsClient.deleteMessage(DeleteMessageRequest.builder()
                                            .queueUrl(queueUrl)
                                            .receiptHandle(message.receiptHandle())
                                            .build());
                                } catch (Exception e) {
                                    log.warn("Failed to delete delayed message from SQS: {}", e.getMessage());
                                }
                                continue;
                            }
                        }

                        java.time.LocalDate today = java.time.LocalDate.now();
                        com.reconflow.entity.ReconciliationRun run = reconciliationRunRepository.findByRunDate(today)
                                .orElseGet(() -> {
                                    com.reconflow.entity.ReconciliationRun newRun = com.reconflow.entity.ReconciliationRun.builder()
                                            .id(UUID.randomUUID())
                                            .runDate(today)
                                            .status("RUNNING")
                                            .startedAt(java.time.ZonedDateTime.now())
                                            .build();
                                    return reconciliationRunRepository.save(newRun);
                                });
                        String runIdStr = run.getId().toString();
                        
                        JobParameters params = new JobParametersBuilder()
                                .addString("runId", runIdStr)
                                .addString("uploadedFileId", uploadedFile.getId().toString())
                                .addString("minAmount", json.has("minAmount") ? json.get("minAmount").asText() : "0.01")
                                .addString("maxAmount", json.has("maxAmount") ? json.get("maxAmount").asText() : "10000000")
                                .addLong("time", System.currentTimeMillis())
                                .toJobParameters();
                        
                        jobLauncher.run(jobToRun, params);

                        // Delete message after successful processing start
                        sqsClient.deleteMessage(DeleteMessageRequest.builder()
                                .queueUrl(queueUrl)
                                .receiptHandle(message.receiptHandle())
                                .build());
                    } else {
                        log.warn("File {} not found or not in QUEUED status", fileId);
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
}
