package com.reconflow.batch;

import com.reconflow.dto.GatewaySettlementRecord;
import com.reconflow.dto.ReconciliationResult;
import com.reconflow.entity.ExceptionQueue;
import com.reconflow.entity.GatewaySettlement;
import com.reconflow.entity.LedgerTransaction;
import com.reconflow.repository.LedgerTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Component
@StepScope
@RequiredArgsConstructor
@Slf4j
public class ReconciliationProcessor implements ItemProcessor<GatewaySettlementRecord, ReconciliationResult> {

    private final LedgerTransactionRepository ledgerTransactionRepository;

    @Value("#{jobParameters['runId']}")
    private String runIdStr;

    @Value("#{jobParameters['uploadedFileId']}")
    private String uploadedFileIdStr;

    @Override
    public ReconciliationResult process(GatewaySettlementRecord record) {
        log.debug("Processing record: {}", record.getGatewayTransactionId());
        
        UUID runId = runIdStr != null ? UUID.fromString(runIdStr) : UUID.randomUUID();
        UUID fileId = uploadedFileIdStr != null ? UUID.fromString(uploadedFileIdStr) : null;

        GatewaySettlement settlement = GatewaySettlement.builder()
                .uploadedFileId(fileId)
                .gateway(record.getGateway())
                .gatewayTransactionId(record.getGatewayTransactionId())
                .orderId(record.getOrderId())
                .settlementBatchId(record.getSettlementId())
                .grossAmount(record.getAmount())
                .feeAmount(record.getFee() != null ? record.getFee() : BigDecimal.ZERO)
                .taxAmount(record.getTax() != null ? record.getTax() : BigDecimal.ZERO)
                .netAmount(record.getAmount() // Usually Gross - Fee - Tax, but let's trust the gateway's gross for simple matching
                        .subtract(record.getFee() != null ? record.getFee() : BigDecimal.ZERO)
                        .subtract(record.getTax() != null ? record.getTax() : BigDecimal.ZERO))
                .settlementDate(record.getTransactionDate().toLocalDate())
                .build();

        Optional<LedgerTransaction> ledgerOpt = ledgerTransactionRepository.findByGatewayTransactionId(record.getGatewayTransactionId());

        if (ledgerOpt.isEmpty()) {
            // EXCEPTION: Missing in Ledger (We received money but have no record of the order)
            ExceptionQueue exception = ExceptionQueue.builder()
                    .reconciliationRunId(runId)
                    .matchId(settlement.getId() != null ? settlement.getId() : UUID.randomUUID()) // Match ID usually points to GatewaySettlement if Ledger is missing
                    .matchType("GATEWAY_SETTLEMENT")
                    .exceptionType("MISSING_IN_LEDGER")
                    .discrepancyAmount(record.getAmount())
                    .currency("INR")
                    .status("OPEN")
                    .build();

            return ReconciliationResult.builder()
                    .gatewaySettlement(settlement)
                    .exception(exception)
                    .build();
        }

        LedgerTransaction ledger = ledgerOpt.get();

        // Check if amount matches
        // Note: Ledger amount is usually gross amount. We compare gross vs gross.
        if (ledger.getAmount().compareTo(record.getAmount()) != 0) {
            // EXCEPTION: Amount Mismatch
            BigDecimal discrepancy = ledger.getAmount().subtract(record.getAmount()).abs();
            
            ExceptionQueue exception = ExceptionQueue.builder()
                    .reconciliationRunId(runId)
                    .matchId(ledger.getId())
                    .matchType("LEDGER_TRANSACTION")
                    .exceptionType("AMOUNT_MISMATCH")
                    .discrepancyAmount(discrepancy)
                    .currency(ledger.getCurrency())
                    .status("OPEN")
                    .build();
                    
            ledger.setStatus("EXCEPTION");

            return ReconciliationResult.builder()
                    .gatewaySettlement(settlement)
                    .matchedLedgerTransaction(ledger)
                    .exception(exception)
                    .build();
        }

        // PERFECT MATCH
        ledger.setStatus("RECONCILED");

        return ReconciliationResult.builder()
                .gatewaySettlement(settlement)
                .matchedLedgerTransaction(ledger)
                .build();
    }
}
