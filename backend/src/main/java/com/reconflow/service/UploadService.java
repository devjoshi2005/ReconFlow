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
    private final com.reconflow.repository.LedgerTransactionRepository ledgerTransactionRepository;
    private final StringRedisTemplate redisTemplate;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.sqs.queue-name}")
    private String queueName;

    @Value("${aws.sqs.queue-url}")
    private String queueUrl;

    private final org.springframework.data.redis.core.script.DefaultRedisScript<List> rateLimitScript;

    @Transactional
    public UploadedFile processUpload(MultipartFile file, String sourceType, String gateway, String bankName, java.time.LocalDate settlementDate, String minAmount, String maxAmount) throws Exception {
        // Rate Limiting (Capacity: 100, Refill: 0.5 per sec)
        String userId = "system";
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            userId = auth.getName();
        }
        
        List<Long> result = redisTemplate.execute(
                rateLimitScript,
                Collections.singletonList("rate:upload:" + userId),
                "100", "0.5", "1"
        );
        
        if (result != null && result.get(0) == 0L) {
            throw new RuntimeException("Rate limit exceeded. Please try again later.");
        }

        // LAYER 1: Synchronous CSV Validation (Header Only)
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(file.getInputStream()))) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.trim().isEmpty()) {
                throw new IllegalArgumentException("File is empty or missing headers.");
            }
            if (headerLine.startsWith("\uFEFF")) {
                headerLine = headerLine.substring(1);
            }
            
            String[] headers = headerLine.split(",");
            java.util.Map<String, Integer> headerMap = new java.util.HashMap<>();
            java.util.Set<String> fileCols = new java.util.HashSet<>();
            for (int i = 0; i < headers.length; i++) {
                String col = headers[i].toLowerCase().trim();
                headerMap.put(col, i);
                fileCols.add(col);
            }

            if ("GATEWAY".equalsIgnoreCase(sourceType)) {
                // Layer 1: Header Checks
                java.util.Map<String, java.util.List<String>> requiredColumns = java.util.Map.of(
                    "RAZORPAY", java.util.List.of("entity_id", "order_id", "amount", "settlement_utr"),
                    "PHONEPE", java.util.List.of("phonepereferenceid", "merchantreferenceid", "amount", "settlementdate"),
                    "PAYU", java.util.List.of("paymentid", "orderid", "grossamount", "settlementutr")
                );
                
                String selectedGateway = gateway.toUpperCase();
                java.util.List<String> required = requiredColumns.get(selectedGateway);
                if (required == null) {
                    throw new IllegalArgumentException("Unknown gateway type: " + gateway);
                }
                
                // Wrong Gateway Heuristic
                for (java.util.Map.Entry<String, java.util.List<String>> entry : requiredColumns.entrySet()) {
                    if (entry.getKey().equals(selectedGateway)) continue;
                    long matchCount = entry.getValue().stream().filter(fileCols::contains).count();
                    if (matchCount >= 3) {
                        throw new IllegalArgumentException("Invalid gateway format. Expected: " + selectedGateway + " but detected structure for " + entry.getKey() + ".");
                    }
                }

                for (String col : required) {
                    if (!fileCols.contains(col)) {
                        throw new IllegalArgumentException("Invalid CSV format. Missing required columns for " + selectedGateway + ".");
                    }
                }
            } else if ("BANK".equalsIgnoreCase(sourceType)) {
                if (!headerMap.containsKey("txn_date") || (!headerMap.containsKey("credit") && !headerMap.containsKey("deposit amt."))) {
                    throw new IllegalArgumentException("Invalid bank statement format. Required columns missing.");
                }
            }
        }

        String checksum = calculateChecksum(file);

        // Idempotency check with Redis (Lock for 30s to prevent double-clicks)
        String idempotencyKey = "upload:idempotency:" + checksum;
        Boolean isNew = redisTemplate.opsForValue().setIfAbsent(idempotencyKey, "processing", Duration.ofSeconds(30));
        
        if (Boolean.FALSE.equals(isNew)) {
            throw new IllegalArgumentException("Request is already processing. Please wait.");
        }

        if (uploadedFileRepository.existsByFileChecksum(checksum)) {
            // Cleanup redis if it was already in DB
            redisTemplate.delete(idempotencyKey);
            throw new IllegalArgumentException("A file with identical content has already been uploaded.");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null && originalFilename.contains("..")) {
            throw new IllegalArgumentException("Invalid file name. Please rename your file and try again.");
        }
        String sanitizedFilename = originalFilename != null ? originalFilename.replaceAll("[^a-zA-Z0-9.-]", "_") : "upload-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0,8) + ".csv";

        String s3Key = "uploads/" + UUID.randomUUID() + "-" + sanitizedFilename;

        // 1. Upload to S3
        s3Client.putObject(PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build(), RequestBody.fromBytes(file.getBytes()));

        // 2. Save DB Record
        UploadedFile uploadedFile = UploadedFile.builder()
                .fileName(sanitizedFilename)
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
                        String messageBody = String.format("{\"fileId\": \"%s\", \"minAmount\": \"%s\", \"maxAmount\": \"%s\"}", fileId, minAmount, maxAmount);
                        
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

    @Transactional
    public UploadedFile processLedgerUpload(MultipartFile file, String gateway, java.time.LocalDate settlementDate) throws Exception {
        // Rate Limiting (Capacity: 100, Refill: 0.5 per sec)
        String userId = "system";
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            userId = auth.getName();
        }
        
        List<Long> result = redisTemplate.execute(
                rateLimitScript,
                Collections.singletonList("rate:upload:" + userId),
                "100", "0.5", "1"
        );
        if (result != null && result.get(0) == 0L) {
            throw new RuntimeException("Rate limit exceeded. Please try again later.");
        }

        String checksum = calculateChecksum(file);
        String idempotencyKey = "upload:idempotency:" + checksum;
        Boolean isNew = redisTemplate.opsForValue().setIfAbsent(idempotencyKey, "processing", Duration.ofSeconds(30));
        
        if (Boolean.FALSE.equals(isNew)) {
            throw new IllegalArgumentException("File is already processing or uploaded.");
        }

        if (uploadedFileRepository.existsByFileChecksum(checksum)) {
            redisTemplate.delete(idempotencyKey);
            throw new IllegalArgumentException("File with this checksum has already been uploaded.");
        }

        List<com.reconflow.entity.LedgerTransaction> batch = new java.util.ArrayList<>();
        int batchSize = 500;
        int rowCount = 0;

        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(file.getInputStream()))) {
            String headerLine = reader.readLine(); // Skip header
            if (headerLine != null && headerLine.startsWith("\uFEFF")) {
                headerLine = headerLine.substring(1);
            }
            String[] headers = headerLine != null ? headerLine.split(",") : new String[0];
            java.util.Map<String, Integer> headerMap = new java.util.HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                headerMap.put(headers[i].trim().toLowerCase(), i);
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(",", -1);
                
                java.util.function.Function<String, String> getCol = (colName) -> {
                    Integer idx = headerMap.get(colName);
                    return (idx != null && idx < parts.length) ? parts[idx].trim() : "";
                };

                String internalOrderId = getCol.apply("internal_order_id");
                if (internalOrderId.matches("^[=+\\-@].*")) internalOrderId = internalOrderId.substring(1);
                if (internalOrderId.length() > 100) internalOrderId = internalOrderId.substring(0, 100);

                String gatewayTxId = getCol.apply("gateway_transaction_id");
                if (gatewayTxId.matches("^[=+\\-@].*")) gatewayTxId = gatewayTxId.substring(1);
                if (gatewayTxId.length() > 100) gatewayTxId = gatewayTxId.substring(0, 100);

                String amountStr = getCol.apply("amount");
                String currencyStr = getCol.apply("currency");
                String gatewayStr = getCol.apply("gateway");
                String dateStr = getCol.apply("transaction_date");
                
                if (amountStr.isEmpty() || dateStr.isEmpty()) continue;

                batch.add(com.reconflow.entity.LedgerTransaction.builder()
                        .internalOrderId(internalOrderId)
                        .gatewayTransactionId(gatewayTxId)
                        .amount(new java.math.BigDecimal(amountStr))
                        .currency(currencyStr)
                        .gateway(gatewayStr)
                        .status("PENDING")
                        .transactionDate(java.time.ZonedDateTime.parse(dateStr + "T00:00:00Z")) // Basic parsing
                        .build());
                        
                // Stream optimization: flush batch to DB to prevent OutOfMemory on huge files
                if (batch.size() >= batchSize) {
                    flushBatch(batch);
                    rowCount += batch.size(); // Note: flushBatch handles duplicates, rowCount might slightly overcount duplicates but it's okay for general stats
                    batch.clear();
                }
            }
            
            // Flush any remaining records in the final chunk
            if (!batch.isEmpty()) {
                flushBatch(batch);
                rowCount += batch.size();
                batch.clear();
            }
        } catch (Exception e) {
            redisTemplate.delete(idempotencyKey);
            throw new RuntimeException("Failed to parse Ledger CSV: " + e.getMessage(), e);
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null && originalFilename.contains("..")) {
            throw new IllegalArgumentException("Invalid filename. Path traversal attempt detected.");
        }
        String sanitizedFilename = originalFilename != null ? originalFilename.replaceAll("[^a-zA-Z0-9.-]", "_") : "upload-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0,8) + ".csv";

        UploadedFile uploadedFile = UploadedFile.builder()
                .fileName(sanitizedFilename)
                .s3Key("DIRECT_DB_UPLOAD") // No S3
                .fileChecksum(checksum)
                .sourceType("LEDGER")
                .gateway(gateway)
                .settlementDate(settlementDate)
                .status("COMPLETED")
                .build();
        
        uploadedFile = uploadedFileRepository.save(uploadedFile);
        
        // Pass rowCount in rawData or transient if needed, but we'll return it in the controller.
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
        return uploadedFileRepository.findAllByOrderByCreatedAtDesc();
    }

    public java.util.Optional<UploadedFile> getFileById(UUID id) {
        return uploadedFileRepository.findById(id);
    }

    public boolean checkCounterpartExists(String sourceType, java.time.LocalDate settlementDate) {
        if (settlementDate == null) return false;
        
        String counterpartSource = "GATEWAY".equalsIgnoreCase(sourceType) ? "BANK" : "GATEWAY";
        return uploadedFileRepository.existsBySourceTypeAndSettlementDate(counterpartSource, settlementDate);
    }
    private void flushBatch(List<com.reconflow.entity.LedgerTransaction> chunk) {
        List<String> orderIds = chunk.stream().map(com.reconflow.entity.LedgerTransaction::getInternalOrderId).toList();
        List<String> existingIds = ledgerTransactionRepository.findByInternalOrderIdIn(orderIds)
                .stream().map(com.reconflow.entity.LedgerTransaction::getInternalOrderId).toList();
        
        List<com.reconflow.entity.LedgerTransaction> toSave = chunk.stream()
                .filter(tx -> !existingIds.contains(tx.getInternalOrderId()))
                .toList();
                
        if (toSave.isEmpty()) return;
        
        String sql = "INSERT INTO ledger_transactions (id, internal_order_id, gateway_transaction_id, amount, currency, gateway, status, transaction_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.batchUpdate(sql, new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
            @Override
            public void setValues(java.sql.PreparedStatement ps, int i) throws java.sql.SQLException {
                com.reconflow.entity.LedgerTransaction tx = toSave.get(i);
                ps.setObject(1, java.util.UUID.randomUUID()); // Generate UUID
                ps.setString(2, tx.getInternalOrderId());
                ps.setString(3, tx.getGatewayTransactionId());
                ps.setBigDecimal(4, tx.getAmount());
                ps.setString(5, tx.getCurrency());
                ps.setString(6, tx.getGateway());
                ps.setString(7, tx.getStatus());
                ps.setObject(8, java.sql.Timestamp.from(tx.getTransactionDate().toInstant()));
            }

            @Override
            public int getBatchSize() {
                return toSave.size();
            }
        });
    }
}
