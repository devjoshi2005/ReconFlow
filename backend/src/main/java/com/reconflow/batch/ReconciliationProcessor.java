package com.reconflow.batch;

import com.reconflow.dto.GatewaySettlementRecord;
import com.reconflow.dto.ReconciliationResult;
import com.reconflow.entity.ChainAMatch;
import com.reconflow.entity.ExceptionQueue;
import com.reconflow.entity.GatewaySettlement;
import com.reconflow.entity.LedgerTransaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
@StepScope
@RequiredArgsConstructor
@Slf4j
public class ReconciliationProcessor implements ItemProcessor<LedgerTransaction, ReconciliationResult> {

    private final CsvInMemoryLoader csvInMemoryLoader;

    @Value("#{jobParameters['runId']}")
    private String runIdStr;

    @Value("#{jobParameters['uploadedFileId']}")
    private String uploadedFileIdStr;

    @Override
    public ReconciliationResult process(LedgerTransaction ledger) {
        UUID runId = runIdStr != null ? UUID.fromString(runIdStr) : UUID.randomUUID();
        
        // O(1) Map Lookup from the in-memory CSV map
        GatewaySettlementRecord gatewayRecord = csvInMemoryLoader.getGatewayRecords().remove(ledger.getGatewayTransactionId());

        if (gatewayRecord == null) {
            // Found in Ledger, but not in the Gateway CSV -> MISSING_IN_GATEWAY
            ExceptionQueue exception = ExceptionQueue.builder()
                    .reconciliationRunId(runId)
                    .matchId(ledger.getId())
                    .matchType("LEDGER_TRANSACTION")
                    .exceptionType("MISSING_IN_GATEWAY")
                    .discrepancyAmount(ledger.getAmount())
                    .currency(ledger.getCurrency())
                    .status("OPEN")
                    .build();

            ledger.setStatus("EXCEPTION");

            return ReconciliationResult.builder()
                    .matchedLedgerTransaction(ledger)
                    .exception(exception)
                    .build();
        }

        // We found a match in the CSV, let's map it to an Entity
        GatewaySettlement settlement = GatewaySettlement.builder()
                .uploadedFileId(csvInMemoryLoader.getCurrentFile().getId())
                .gateway(gatewayRecord.getGateway())
                .gatewayTransactionId(gatewayRecord.getGatewayTransactionId())
                .orderId(gatewayRecord.getOrderId())
                .settlementBatchId(gatewayRecord.getSettlementId())
                .grossAmount(gatewayRecord.getAmount())
                .feeAmount(gatewayRecord.getFee() != null ? gatewayRecord.getFee() : BigDecimal.ZERO)
                .taxAmount(gatewayRecord.getTax() != null ? gatewayRecord.getTax() : BigDecimal.ZERO)
                .netAmount(gatewayRecord.getAmount()
                        .subtract(gatewayRecord.getFee() != null ? gatewayRecord.getFee() : BigDecimal.ZERO)
                        .subtract(gatewayRecord.getTax() != null ? gatewayRecord.getTax() : BigDecimal.ZERO))
                .settlementDate(gatewayRecord.getTransactionDate().toLocalDate())
                .build();

        // Compare Amounts
        if (ledger.getAmount().compareTo(gatewayRecord.getAmount()) != 0) {
            // Amount Mismatch Exception
            BigDecimal discrepancy = ledger.getAmount().subtract(gatewayRecord.getAmount()).abs();
            
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
                    .matchedLedgerTransaction(ledger)
                    .gatewaySettlement(settlement)
                    .exception(exception)
                    .build();
        }

        // Perfect Match
        ledger.setStatus("MATCHED");
        
        ChainAMatch match = ChainAMatch.builder()
                .reconciliationRunId(runId)
                .ledgerTransactionId(ledger.getId())
                // .gatewaySettlementId(...) will be set by Writer after saving the settlement
                .matchStatus("MATCHED")
                .build();

        return ReconciliationResult.builder()
                .matchedLedgerTransaction(ledger)
                .gatewaySettlement(settlement)
                .chainAMatch(match)
                .build();
    }
}
