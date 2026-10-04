package com.reconflow.batch;

import com.reconflow.entity.UploadedFile;
import com.reconflow.repository.UploadedFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReconciliationJobListener implements JobExecutionListener {

    private final UploadedFileRepository uploadedFileRepository;
    private final StringRedisTemplate redisTemplate;

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("Starting Reconciliation Job: {}", jobExecution.getJobInstance().getJobName());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        String uploadedFileIdStr = jobExecution.getJobParameters().getString("uploadedFileId");
        
        if (uploadedFileIdStr != null) {
            try {
                UUID fileId = UUID.fromString(uploadedFileIdStr);
                UploadedFile file = uploadedFileRepository.findById(fileId).orElse(null);
                
                if (file != null) {
                    if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
                        file.setStatus("COMPLETED");
                    } else {
                        file.setStatus("FAILED");
                    }
                    uploadedFileRepository.save(file);
                    log.info("Updated file {} status to {}", fileId, file.getStatus());
                }
            } catch (Exception e) {
                log.error("Failed to update file status on job completion", e);
            }
        }

        // Invalidate Analytics Cache and Publish Job Completion Event
        try {
            redisTemplate.delete(redisTemplate.keys("analytics:summary:*"));
            log.info("Invalidated Redis analytics cache after job completion.");
            
            String eventMessage = "{\"fileId\":\"" + uploadedFileIdStr + "\", \"status\":\"" + jobExecution.getStatus() + "\"}";
            redisTemplate.convertAndSend("job:events", eventMessage);
            log.info("Published job completion event to Redis: {}", eventMessage);
        } catch (Exception e) {
            log.error("Failed to interact with Redis after job completion", e);
        }
    }
}
