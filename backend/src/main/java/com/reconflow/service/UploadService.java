package com.reconflow.service;

import com.reconflow.entity.UploadedFile;
import com.reconflow.repository.UploadedFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.FileCopyUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UploadService {

    private final S3Client s3Client;
    private final SqsClient sqsClient;
    private final UploadedFileRepository uploadedFileRepository;
    private final StringRedisTemplate redisTemplate;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.sqs.queue-name}")
    private String queueName;

    @Value("${aws.sqs.queue-url}")
    private String queueUrl;

    private RedisScript<List> rateLimitScript;

    @jakarta.annotation.PostConstruct
    public void init() {
        try {
            ClassPathResource resource = new ClassPathResource("scripts/token_bucket.lua");
            byte[] bytes = FileCopyUtils.copyToByteArray(resource.getInputStream());
            String scriptContent = new String(bytes, StandardCharsets.UTF_8);
            rateLimitScript = new DefaultRedisScript<>(scriptContent, List.class);
        } catch (IOException e) {
            log.error("Failed to load rate limiting script", e);
        }
    }

    @Transactional
    public UploadedFile processUpload(MultipartFile file, String sourceType, String gateway, String bankName, java.time.LocalDate settlementDate) throws Exception {
        // Rate Limiting (Capacity: 10, Refill: 0.5 per sec)
        String userId = "system"; // Mocking user for now, in real scenario fetch from SecurityContext
        List<Long> result = redisTemplate.execute(
                rateLimitScript,
                Collections.singletonList("rate:upload:" + userId),
                "10", "0.5", "1"
        );
        
        if (result != null && result.get(0) == 0L) {
            throw new RuntimeException("Rate limit exceeded. Please try again later.");
        }

        String checksum = calculateChecksum(file);

        // Idempotency check with Redis (Lock for 30s to prevent double-clicks)
        String idempotencyKey = "upload:idempotency:" + checksum;
        Boolean isNew = redisTemplate.opsForValue().setIfAbsent(idempotencyKey, "processing", Duration.ofSeconds(30));
        
        if (Boolean.FALSE.equals(isNew)) {
            throw new IllegalArgumentException("File is already processing or uploaded.");
        }

        if (uploadedFileRepository.existsByFileChecksum(checksum)) {
            // Cleanup redis if it was already in DB
            redisTemplate.delete(idempotencyKey);
            throw new IllegalArgumentException("File with this checksum has already been uploaded.");
        }

        String s3Key = "uploads/" + UUID.randomUUID() + "-" + file.getOriginalFilename();

        // 1. Upload to S3
        s3Client.putObject(PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build(), RequestBody.fromBytes(file.getBytes()));

        // 2. Save DB Record
        UploadedFile uploadedFile = UploadedFile.builder()
                .fileName(file.getOriginalFilename())
                .s3Key(s3Key)
                .fileChecksum(checksum)
                .sourceType(sourceType)
                .gateway(gateway)
                .bankName(bankName)
                .settlementDate(settlementDate)
                .status("QUEUED")
                .build();
        
        uploadedFile = uploadedFileRepository.save(uploadedFile);

        // 3. Push to SQS after DB commit to prevent listener race conditions
        final UUID fileId = uploadedFile.getId();
        final String finalQueueUrl = this.queueUrl;
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        String messageBody = String.format("{\"fileId\": \"%s\"}", fileId);
                        
                        sqsClient.sendMessage(SendMessageRequest.builder()
                                .queueUrl(finalQueueUrl)
                                .messageBody(messageBody)
                                .build());
                        log.info("Successfully queued file upload: {}", fileId);
                    } catch (Exception e) {
                        log.error("Failed to send SQS message after commit for file: {}", fileId, e);
                    }
                }
            }
        );

        return uploadedFile;
    }

    private String calculateChecksum(MultipartFile file) throws NoSuchAlgorithmException, IOException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream fis = file.getInputStream()) {
            byte[] byteArray = new byte[1024];
            int bytesCount;
            while ((bytesCount = fis.read(byteArray)) != -1) {
                digest.update(byteArray, 0, bytesCount);
            }
        }
        byte[] bytes = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte aByte : bytes) {
            sb.append(Integer.toString((aByte & 0xff) + 0x100, 16).substring(1));
        }
        return sb.toString();
    }

    public java.util.List<UploadedFile> getAllFiles() {
        return uploadedFileRepository.findAll();
    }

    public java.util.Optional<UploadedFile> getFileById(UUID id) {
        return uploadedFileRepository.findById(id);
    }

    public boolean checkCounterpartExists(String sourceType, java.time.LocalDate settlementDate) {
        if (settlementDate == null) return false;
        
        String counterpartSource = "GATEWAY".equalsIgnoreCase(sourceType) ? "BANK" : "GATEWAY";
        return uploadedFileRepository.existsBySourceTypeAndSettlementDate(counterpartSource, settlementDate);
    }
}
