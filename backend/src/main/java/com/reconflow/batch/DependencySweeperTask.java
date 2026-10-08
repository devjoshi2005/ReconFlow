package com.reconflow.batch;

import com.reconflow.entity.UploadedFile;
import com.reconflow.repository.UploadedFileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.List;

@Slf4j
@Component
public class DependencySweeperTask {

    @Autowired
    private UploadedFileRepository uploadedFileRepository;

    @Autowired
    private SqsClient sqsClient;

    @Value("${aws.sqs.queue-url}")
    private String queueUrl;

    /**
     * Runs every 60 seconds (by default) to check if any files waiting for dependencies
     * can now be processed because their prerequisites have arrived.
     */
    @Scheduled(fixedDelayString = "${reconflow.sweeper.delay:60000}")
    public void sweep() {
        log.debug("Starting Dependency Sweeper...");
        List<UploadedFile> pendingFiles = uploadedFileRepository.findByStatus("PENDING_DEPENDENCY");

        if (pendingFiles.isEmpty()) {
            return;
        }

        log.info("Sweeper found {} files pending dependencies.", pendingFiles.size());

        for (UploadedFile file : pendingFiles) {
            boolean canProcess = false;

            if ("GATEWAY".equalsIgnoreCase(file.getSourceType())) {
                canProcess = uploadedFileRepository.existsBySourceTypeAndSettlementDateAndStatus(
                        "LEDGER", file.getSettlementDate(), "COMPLETED");
            } else if ("BANK".equalsIgnoreCase(file.getSourceType())) {
                // Bank files don't have a specific gateway, they depend on ANY Gateway file for that date
                canProcess = uploadedFileRepository.existsBySourceTypeAndSettlementDateAndStatus(
                        "GATEWAY", file.getSettlementDate(), "COMPLETED");
            } else if ("LEDGER".equalsIgnoreCase(file.getSourceType())) {
                // Ledger has no prerequisites, so if it's PENDING_DEPENDENCY something is wrong.
                canProcess = true; 
            }

            if (canProcess) {
                log.info("Dependencies met for file ID {}. Re-queueing for processing.", file.getId());
                file.setStatus("QUEUED");
                uploadedFileRepository.save(file);

                String messageBody = String.format("{\"fileId\":\"%s\", \"sourceType\":\"%s\", \"gateway\":\"%s\"}",
                        file.getId(), file.getSourceType(), file.getGateway());

                sqsClient.sendMessage(SendMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .messageBody(messageBody)
                        .build());
            }
        }
    }
}
