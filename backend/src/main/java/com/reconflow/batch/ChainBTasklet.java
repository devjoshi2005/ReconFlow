package com.reconflow.batch;

import com.reconflow.dto.BankStatementRecord;
import com.reconflow.entity.ChainBMatch;
import com.reconflow.entity.ExceptionQueue;
import com.reconflow.entity.GatewaySettlement;
import com.reconflow.repository.ChainBMatchRepository;
import com.reconflow.repository.ExceptionQueueRepository;
import com.reconflow.repository.GatewaySettlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Component
@StepScope
@RequiredArgsConstructor
@Slf4j
public class ChainBTasklet implements Tasklet {

    private final CsvInMemoryLoader csvInMemoryLoader;
    private final GatewaySettlementRepository gatewaySettlementRepository;
    private final ChainBMatchRepository chainBMatchRepository;
    private final ExceptionQueueRepository exceptionQueueRepository;

    @Value("#{jobParameters['runId']}")
    private String runIdStr;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        UUID runId = runIdStr != null ? UUID.fromString(runIdStr) : UUID.randomUUID();
        
        // The loader has the Bank Statements indexed by UTR
        Map<String, BankStatementRecord> bankRecords = csvInMemoryLoader.getBankRecords();
        log.info("Starting Chain B Tasklet. {} Bank Records loaded.", bankRecords.size());

        // We fetch all GatewaySettlements that have a UTR (bankTransferRef) but are not yet matched in Chain B.
        // For simplicity in this mock, we just group all Gateway Settlements by UTR.
        // Realistically, we'd only query those where chainBMatched = false.
        List<GatewaySettlement> allSettlements = gatewaySettlementRepository.findAll();
        
        Map<String, List<GatewaySettlement>> groupedByUtr = allSettlements.stream()
                .filter(gs -> gs.getBankTransferRef() != null && !gs.getBankTransferRef().isEmpty())
                .collect(Collectors.groupingBy(GatewaySettlement::getBankTransferRef));

        List<ChainBMatch> matchesToSave = new ArrayList<>();
        List<ExceptionQueue> exceptionsToSave = new ArrayList<>();

        for (Map.Entry<String, List<GatewaySettlement>> entry : groupedByUtr.entrySet()) {
            String utr = entry.getKey();
            List<GatewaySettlement> settlements = entry.getValue();

            BigDecimal sumNetAmount = settlements.stream()
                    .map(GatewaySettlement::getNetAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BankStatementRecord bankRecord = bankRecords.remove(utr);

            if (bankRecord == null) {
                // We have a gateway settlement batch, but no bank statement found yet -> MISSING_IN_BANK
                // (Could just be bank statement is delayed)
                ExceptionQueue exception = ExceptionQueue.builder()
                        .reconciliationRunId(runId)
                        .matchId(settlements.get(0).getId()) // attach to first settlement for traceability
                        .matchType("GATEWAY_SETTLEMENT_BATCH")
                        .exceptionType("MISSING_IN_BANK")
                        .discrepancyAmount(sumNetAmount)
                        .currency("INR")
                        .status("OPEN")
                        .build();
                exceptionsToSave.add(exception);
            } else {
                // Compare Sum Net Amount vs Bank Credit Amount
                if (sumNetAmount.compareTo(bankRecord.getCreditAmount()) != 0) {
                    BigDecimal discrepancy = sumNetAmount.subtract(bankRecord.getCreditAmount()).abs();
                    ExceptionQueue exception = ExceptionQueue.builder()
                            .reconciliationRunId(runId)
                            .matchId(settlements.get(0).getId())
                            .matchType("GATEWAY_SETTLEMENT_BATCH")
                            .exceptionType("NET_AMOUNT_MISMATCH")
                            .discrepancyAmount(discrepancy)
                            .currency("INR")
                            .status("OPEN")
                            .build();
                    exceptionsToSave.add(exception);
                } else {
                    // Match!
                    ChainBMatch match = ChainBMatch.builder()
                            .reconciliationRunId(runId)
                            .gatewaySettlementId(settlements.get(0).getId())
                            .bankStatementId(UUID.randomUUID()) // Mocking the bank statement persistence
                            .matchStatus("MATCHED")
                            .build();
                    matchesToSave.add(match);
                }
            }
        }

        // Remaining bank records have no corresponding Gateway batch -> MISSING_IN_GATEWAY
        for (BankStatementRecord remainingBank : bankRecords.values()) {
            ExceptionQueue exception = ExceptionQueue.builder()
                    .reconciliationRunId(runId)
                    .matchId(UUID.randomUUID())
                    .matchType("BANK_STATEMENT")
                    .exceptionType("MISSING_IN_GATEWAY")
                    .discrepancyAmount(remainingBank.getCreditAmount())
                    .currency("INR")
                    .status("OPEN")
                    .build();
            exceptionsToSave.add(exception);
        }

        if (!matchesToSave.isEmpty()) {
            chainBMatchRepository.saveAll(matchesToSave);
            log.info("Saved {} Chain B matches.", matchesToSave.size());
        }

        if (!exceptionsToSave.isEmpty()) {
            exceptionQueueRepository.saveAll(exceptionsToSave);
            log.info("Saved {} exceptions during Chain B.", exceptionsToSave.size());
        }

        return RepeatStatus.FINISHED;
    }
}
