package com.reconflow.batch;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.reconflow.dto.BankStatementRecord;
import com.reconflow.dto.GatewaySettlementRecord;
import com.reconflow.entity.UploadedFile;
import com.reconflow.repository.UploadedFileRepository;
import com.reconflow.repository.ExceptionQueueRepository;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@StepScope
@RequiredArgsConstructor
@Slf4j
public class CsvInMemoryLoader implements StepExecutionListener {

    private final S3Client s3Client;
    private final UploadedFileRepository uploadedFileRepository;

    private final ExceptionQueueRepository exceptionQueueRepository;
    private final com.reconflow.repository.GatewaySettlementRepository gatewaySettlementRepository;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("#{jobParameters['uploadedFileId']}")
    private String uploadedFileIdStr;

    @Value("#{jobParameters['runId']}")
    private String runIdStr;

    @Getter
    private final ConcurrentHashMap<String, GatewaySettlementRecord> gatewayRecords = new ConcurrentHashMap<>();
    
    @Getter
    private final ConcurrentHashMap<String, BankStatementRecord> bankRecords = new ConcurrentHashMap<>();

    @Getter
    private UploadedFile currentFile;

    @Override
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
                org.springframework.batch.item.file.FlatFileItemReader<GatewaySettlementRecord> readerBean = null;
                com.reconflow.batch.GatewayReaderConfig config = new com.reconflow.batch.GatewayReaderConfig();
                
                if ("RAZORPAY".equalsIgnoreCase(currentFile.getGateway())) {
                    readerBean = config.razorpayCsvReader(null);
                } else if ("PHONEPE".equalsIgnoreCase(currentFile.getGateway())) {
                    readerBean = config.phonePeCsvReader(null);
                } else {
                    throw new IllegalArgumentException("Unsupported Gateway: " + currentFile.getGateway());
                }
                
                readerBean.setResource(new org.springframework.core.io.InputStreamResource(s3Object));
                readerBean.open(stepExecution.getExecutionContext());
                
                GatewaySettlementRecord record;
                while ((record = readerBean.read()) != null) {
                    record.setGateway(currentFile.getGateway()); // Enrich with context
                    gatewayRecords.put(record.getGatewayTransactionId(), record);
                }
                readerBean.close();
                log.info("Loaded {} Gateway rows into HashMap", gatewayRecords.size());

            } else if ("BANK".equalsIgnoreCase(currentFile.getSourceType())) {
                com.opencsv.bean.CsvToBean<BankStatementRecord> csvToBean = new com.opencsv.bean.CsvToBeanBuilder<BankStatementRecord>(reader)
                        .withType(BankStatementRecord.class)
                        .withIgnoreLeadingWhiteSpace(true)
                        .build();

                java.util.List<BankStatementRecord> records = csvToBean.parse();
                for (BankStatementRecord record : records) {
                    record.setBankName(currentFile.getBankName()); // Enrich
                    bankRecords.put(record.getUtr(), record);
                }
                log.info("Loaded {} Bank Statement rows into HashMap", bankRecords.size());
            }

        } catch (Exception e) {
            log.error("Failed to load CSV into memory", e);
            throw new RuntimeException("Failed to load CSV", e);
        }
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        if ("GATEWAY".equalsIgnoreCase(currentFile.getSourceType()) && !gatewayRecords.isEmpty()) {
            log.warn("Found {} Gateway settlements with no matching Ledger transaction (MISSING_IN_LEDGER).", gatewayRecords.size());
            UUID runId = runIdStr != null ? UUID.fromString(runIdStr) : UUID.randomUUID();
            
            List<com.reconflow.entity.ExceptionQueue> exceptions = new java.util.ArrayList<>();
            List<com.reconflow.entity.GatewaySettlement> settlements = new java.util.ArrayList<>();
            
            for (GatewaySettlementRecord record : gatewayRecords.values()) {
                com.reconflow.entity.GatewaySettlement settlement = com.reconflow.entity.GatewaySettlement.builder()
                    .uploadedFileId(currentFile.getId())
                    .gateway(record.getGateway())
                    .gatewayTransactionId(record.getGatewayTransactionId())
                    .orderId(record.getOrderId())
                    .settlementBatchId(record.getSettlementId())
                    .grossAmount(record.getAmount())
                    .feeAmount(record.getFee() != null ? record.getFee() : java.math.BigDecimal.ZERO)
                    .taxAmount(record.getTax() != null ? record.getTax() : java.math.BigDecimal.ZERO)
                    .netAmount(record.getAmount()
                            .subtract(record.getFee() != null ? record.getFee() : java.math.BigDecimal.ZERO)
                            .subtract(record.getTax() != null ? record.getTax() : java.math.BigDecimal.ZERO))
                    .settlementDate(record.getTransactionDate().toLocalDate())
                    .build();
                
                settlements.add(settlement);
                
                com.reconflow.entity.ExceptionQueue exception = com.reconflow.entity.ExceptionQueue.builder()
                        .reconciliationRunId(runId)
                        .matchId(settlement.getId()) // Ensure we generate ID for settlement via @PrePersist or UUID generator beforehand.
                        .matchType("GATEWAY_SETTLEMENT")
                        .exceptionType("MISSING_IN_LEDGER")
                        .discrepancyAmount(record.getAmount())
                        .currency("INR")
                        .status("OPEN")
                        .build();
                exceptions.add(exception);
            }
            
            gatewaySettlementRepository.saveAll(settlements);
            exceptionQueueRepository.saveAll(exceptions);
        }
        
        return null;
    }
}
