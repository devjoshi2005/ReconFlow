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
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SqsJobListenerService {

    private final SqsClient sqsClient;
    private final UploadedFileRepository uploadedFileRepository;
    private final JobLauncher jobLauncher;
    private final Job razorpayReconciliationJob;
    private final Job phonePeReconciliationJob;
    private final S3Client s3Client;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Value("${aws.sqs.queue-name:reconciliation-jobs}")
    private String queueName;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Scheduled(fixedDelay = 10000) // Poll every 10 seconds
    public void pollMainQueue() {
        try {
            String queueUrl = sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build()).queueUrl();
            
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
                        log.info("Starting Batch Job for File ID: {}, Gateway: {}", fileId, uploadedFile.getGateway());
                        
                        // Mark as processing
                        uploadedFile.setStatus("PROCESSING");
                        uploadedFileRepository.save(uploadedFile);

                        // Download from S3
                        java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("recon-", "-" + uploadedFile.getFileName());
                        s3Client.getObject(software.amazon.awssdk.services.s3.model.GetObjectRequest.builder()
                                .bucket(bucketName)
                                .key(uploadedFile.getS3Key())
                                .build(), tempFile);

                        // Determine Job
                        Job jobToRun = "PHONEPE".equalsIgnoreCase(uploadedFile.getGateway()) ? phonePeReconciliationJob : razorpayReconciliationJob;
                        
                        JobParameters params = new JobParametersBuilder()
                                .addString("runId", UUID.randomUUID().toString())
                                .addString("uploadedFileId", uploadedFile.getId().toString())
                                .addString("filePath", tempFile.toAbsolutePath().toString())
                                .addLong("time", System.currentTimeMillis())
                                .toJobParameters();
                        
                        jobLauncher.run(jobToRun, params);

                        // Delete message after successful processing start
                        sqsClient.deleteMessage(DeleteMessageRequest.builder()
                                .queueUrl(queueUrl)
                                .receiptHandle(message.receiptHandle())
                                .build());
                                
                        uploadedFile.setStatus("COMPLETED");
                        uploadedFileRepository.save(uploadedFile);
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
