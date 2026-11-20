package com.reconflow.batch;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.reconflow.dto.BankStatementRecord;
import com.reconflow.dto.GatewaySettlementRecord;
import com.reconflow.entity.ExceptionQueue;
import com.reconflow.entity.GatewaySettlement;
import com.reconflow.entity.UploadedFile;
import com.reconflow.entity.status.ExceptionStatus;
import com.reconflow.repository.ExceptionQueueRepository;
import com.reconflow.repository.GatewaySettlementRepository;
import com.reconflow.repository.UploadedFileRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@StepScope
@RequiredArgsConstructor
@Slf4j
public class CsvInMemoryLoader implements StepExecutionListener {

    private final S3Client s3Client;
    private final UploadedFileRepository uploadedFileRepository;
    
    private final List<GatewayFileAdapter> adapters;
    private final CsvHeaderMapper headerMapper;

    private final ExceptionQueueRepository exceptionQueueRepository;
    private final com.reconflow.repository.GatewaySettlementRepository gatewaySettlementRepository;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("#{jobParameters['uploadedFileId']}")
    private String uploadedFileIdStr;

    @Value("#{jobParameters['runId']}")
    private String runIdStr;

    @Value("#{jobParameters['minAmount']}")
    private String minAmountStr;

    @Value("#{jobParameters['maxAmount']}")
    private String maxAmountStr;

    @Getter
    private final ConcurrentHashMap<String, GatewaySettlementRecord> gatewayRecords = new ConcurrentHashMap<>();
    
    @Getter
    private final ConcurrentHashMap<String, BankStatementRecord> bankRecords = new ConcurrentHashMap<>();

    @Getter
    private UploadedFile currentFile;

    @Override
    @org.springframework.batch.core.annotation.BeforeStep
    public void beforeStep(StepExecution stepExecution) {
        if (uploadedFileIdStr == null) {
            throw new IllegalArgumentException("uploadedFileId job parameter is missing");
        }

        UUID fileId = UUID.fromString(uploadedFileIdStr);
        currentFile = uploadedFileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("UploadedFile not found: " + fileId));

        log.info("Loading CSV file {} into memory for fast O(1) matching", currentFile.getS3Key());

        try (ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(GetObjectRequest.builder()
                .bucket(bucketName)
                .key(currentFile.getS3Key())
                .build());
             Reader reader = new InputStreamReader(s3Object)) {

            if ("GATEWAY".equalsIgnoreCase(currentFile.getSourceType())) {
                com.opencsv.CSVReader csvReader = new com.opencsv.CSVReader(reader);
                String[] headers = csvReader.readNext();
                if (headers == null) throw new IllegalArgumentException("CSV is empty");
                
                java.util.Map<String, Integer> headerMap = headerMapper.buildHeaderIndex(headers);

                String gateway = currentFile.getGateway().toUpperCase();

                GatewayFileAdapter adapter = adapters.stream()
                        .filter(a -> a.getGatewayName().toUpperCase().equals(gateway))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("No adapter found for gateway: " + gateway));

                String[] line;
                while ((line = csvReader.readNext()) != null) {
                    if (line.length == 0 || (line.length == 1 && line[0].trim().isEmpty())) continue;

                    try {
                        GatewaySettlementRecord record = adapter.parse(headerMap, line, headerMapper);
                        
                        if (record.getGatewayTransactionId() != null) {
                            record.setGatewayTransactionId(sanitizeAndTruncate(record.getGatewayTransactionId(), 100));
                            if (record.getOrderId() != null) record.setOrderId(sanitizeAndTruncate(record.getOrderId(), 100));
                            if (record.getBankTransferRef() != null) record.setBankTransferRef(sanitizeAndTruncate(record.getBankTransferRef(), 100));
                            
                            if (gatewayRecords.containsKey(record.getGatewayTransactionId())) {
                                log.warn("Duplicate Gateway ID found in file: {}", record.getGatewayTransactionId());
                                
                                UUID runId = runIdStr != null ? UUID.fromString(runIdStr) : UUID.randomUUID();
                                UUID matchId = UUID.nameUUIDFromBytes(
                                        (runId + "|DUPLICATE_SETTLEMENT|" + record.getGatewayTransactionId())
                                                .getBytes(StandardCharsets.UTF_8));
                                ExceptionQueue duplicateException = ExceptionQueue.builder()
                                        .reconciliationRunId(runId)
                                        .matchId(matchId)
                                        .matchType("A")
                                        .exceptionType("DUPLICATE_SETTLEMENT")
                                        .discrepancyAmount(record.getAmount())
                                        .currency("INR")
                                        .status(ExceptionStatus.OPEN)
                                        .gateway(record.getGateway())
                                        .build();
                                if (isNew(duplicateException)) {
                                    exceptionQueueRepository.save(duplicateException);
                                }
                            }
                            gatewayRecords.put(record.getGatewayTransactionId(), record);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to parse row: {}", String.join(",", line), e);
                    }
                }
                csvReader.close();
                log.info("Loaded {} Gateway rows into HashMap", gatewayRecords.size());
                
                if (gatewayRecords.isEmpty()) {
                    throw new RuntimeException("0 rows parsed from Gateway CSV. Is the format correct?");
                }
                
                // Layer 3: Post-Parse Data Sanity Checks
                long negativeAmounts = gatewayRecords.values().stream()
                        .filter(r -> r.getAmount() != null && r.getAmount().compareTo(java.math.BigDecimal.ZERO) < 0)
                        .count();
                if (negativeAmounts > 0) {
                    log.warn("{} rows have negative amounts. Verifying these are refunds...", negativeAmounts);
                }
                
                java.math.BigDecimal minAmount = minAmountStr != null ? new java.math.BigDecimal(minAmountStr) : new java.math.BigDecimal("0.01");
                java.math.BigDecimal maxAmount = maxAmountStr != null ? new java.math.BigDecimal(maxAmountStr) : new java.math.BigDecimal("10000000");

                long outliers = gatewayRecords.values().stream()
                        .filter(r -> r.getAmount() != null && (
                                r.getAmount().compareTo(minAmount) < 0 || 
                                r.getAmount().compareTo(maxAmount) > 0)
                        ).count();
                if (outliers > 0) {
                    log.warn("{} rows have amounts outside expected range (₹{} to ₹{}). Check for paise/rupee confusion or malformed data.", outliers, minAmount, maxAmount);
                }

            } else if ("BANK".equalsIgnoreCase(currentFile.getSourceType())) {
                com.opencsv.bean.CsvToBean<BankStatementRecord> csvToBean = new com.opencsv.bean.CsvToBeanBuilder<BankStatementRecord>(reader)
                        .withType(BankStatementRecord.class)
                        .withIgnoreLeadingWhiteSpace(true)
                        .withIgnoreEmptyLine(true)
                        .build();

                java.util.List<BankStatementRecord> records = csvToBean.parse();
                for (BankStatementRecord record : records) {
                    record.setBankName(currentFile.getBankName()); // Enrich
                    
                    if (record.getUtr() != null) record.setUtr(sanitizeAndTruncate(record.getUtr(), 100));
                    if (record.getNarration() != null) record.setNarration(sanitizeAndTruncate(record.getNarration(), 500));
                    
                    bankRecords.put(record.getUtr(), record);
                }
                log.info("Loaded {} Bank Statement rows into HashMap", bankRecords.size());
            }

        } catch (Exception e) {
            log.error("Failed to load CSV into memory", e);
            try {
                java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter("/tmp/recon_error.log"));
                e.printStackTrace(pw);
                pw.close();
            } catch (Exception ex) {}
            throw new RuntimeException("Failed to load CSV", e);
        }
    }

    private String sanitizeAndTruncate(String value, int maxLength) {
        if (value == null) return null;
        value = value.trim();
        if (value.matches("^[=+\\-@].*")) {
            value = value.substring(1);
        }
        if (value.length() > maxLength) {
            value = value.substring(0, maxLength);
        }
        return value;
    }

    @Override
    @org.springframework.batch.core.annotation.AfterStep
    public ExitStatus afterStep(StepExecution stepExecution) {
        if (!"GATEWAY".equalsIgnoreCase(currentFile.getSourceType()) || gatewayRecords.isEmpty()) {
            return null;
        }

        UUID runId = runIdStr != null ? UUID.fromString(runIdStr) : UUID.randomUUID();
        List<GatewaySettlementRecord> unmatchedRecords = List.copyOf(gatewayRecords.values());
        List<String> transactionIds = unmatchedRecords.stream()
                .map(GatewaySettlementRecord::getGatewayTransactionId)
                .toList();

        // Existing and newly saved settlements share one lookup, so retries always use
        // the same persisted settlement id as the exception match id.
        Map<String, GatewaySettlement> settlementsByTransaction = new HashMap<>();
        gatewaySettlementRepository
                .findByUploadedFileIdAndGatewayTransactionIdIn(currentFile.getId(), transactionIds)
                .forEach(settlement -> settlementsByTransaction.put(settlement.getGatewayTransactionId(), settlement));

        List<GatewaySettlement> newSettlements = unmatchedRecords.stream()
                .filter(record -> !settlementsByTransaction.containsKey(record.getGatewayTransactionId()))
                .map(this::toSettlement)
                .toList();
        if (!newSettlements.isEmpty()) {
            gatewaySettlementRepository.saveAll(newSettlements)
                    .forEach(settlement -> settlementsByTransaction.put(settlement.getGatewayTransactionId(), settlement));
            log.info("Saved {} unmatched gateway settlements.", newSettlements.size());
        }

        List<ExceptionQueue> newExceptions = unmatchedRecords.stream()
                .map(record -> missingInLedgerException(
                        runId, settlementsByTransaction.get(record.getGatewayTransactionId())))
                .filter(this::isNew)
                .toList();
        if (!newExceptions.isEmpty()) {
            exceptionQueueRepository.saveAll(newExceptions);
            log.warn("Generated {} MISSING_IN_LEDGER exceptions.", newExceptions.size());
        }
        return null;
    }

    private GatewaySettlement toSettlement(GatewaySettlementRecord record) {
        BigDecimal fee = record.getFee() != null ? record.getFee() : BigDecimal.ZERO;
        BigDecimal tax = record.getTax() != null ? record.getTax() : BigDecimal.ZERO;
        return GatewaySettlement.builder()
                .uploadedFileId(currentFile.getId())
                .gateway(record.getGateway())
                .gatewayTransactionId(record.getGatewayTransactionId())
                .orderId(record.getOrderId())
                .settlementBatchId(record.getSettlementId())
                .grossAmount(record.getAmount())
                .feeAmount(fee)
                .taxAmount(tax)
                .netAmount(record.getAmount().subtract(fee).subtract(tax))
                .bankTransferRef(record.getBankTransferRef())
                .settlementDate(currentFile.getSettlementDate() != null
                        ? currentFile.getSettlementDate()
                        : record.getTransactionDate().toLocalDate())
                .build();
    }

    private ExceptionQueue missingInLedgerException(UUID runId, GatewaySettlement settlement) {
        return ExceptionQueue.builder()
                .reconciliationRunId(runId)
                .matchId(settlement.getId())
                .matchType("A")
                .exceptionType("MISSING_IN_LEDGER")
                .discrepancyAmount(settlement.getGrossAmount())
                .currency("INR")
                .status(ExceptionStatus.OPEN)
                .gateway(settlement.getGateway())
                .build();
    }

    private boolean isNew(ExceptionQueue exception) {
        return !exceptionQueueRepository.existsByReconciliationRunIdAndMatchIdAndExceptionType(
                exception.getReconciliationRunId(), exception.getMatchId(), exception.getExceptionType());
    }
}
