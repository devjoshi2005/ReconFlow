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

        // The bank records are loaded from the CSV.
        List<BankStatementRecord> allBankRecords = new ArrayList<>(bankRecords.values());
        Set<String> matchedGatewayUtrs = new HashSet<>();

        for (BankStatementRecord bankRecord : allBankRecords) {
            String narration = bankRecord.getNarration() != null ? bankRecord.getNarration() : "";
            boolean matchedAny = false;

            for (Map.Entry<String, List<GatewaySettlement>> entry : groupedByUtr.entrySet()) {
                String knownUtr = entry.getKey();
                
                // PRODUCTION APPROACH: The UTR from the gateway CSV appears IN the bank narration
                if (narration.contains(knownUtr)) {
                    matchedAny = true;
                    matchedGatewayUtrs.add(knownUtr);
                    
                    List<GatewaySettlement> settlements = entry.getValue();
                    BigDecimal sumNetAmount = settlements.stream()
                            .map(GatewaySettlement::getNetAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    if (sumNetAmount.compareTo(bankRecord.getCreditAmount()) != 0) {
                        BigDecimal discrepancy = sumNetAmount.subtract(bankRecord.getCreditAmount()).abs();
                        ExceptionQueue exception = ExceptionQueue.builder()
                                .reconciliationRunId(runId)
                                .matchId(settlements.get(0).getId())
                                .matchType("B")
                                .exceptionType("NET_AMOUNT_MISMATCH")
                                .discrepancyAmount(discrepancy)
                                .currency("INR")
                                .status("OPEN")
                                .build();
                        exceptionsToSave.add(exception);
                    } else {
                        ChainBMatch match = ChainBMatch.builder()
                                .reconciliationRunId(runId)
                                .gatewaySettlementId(settlements.get(0).getId())
                                .bankStatementId(UUID.randomUUID())
                                .matchStatus("MATCHED")
                                .build();
                        matchesToSave.add(match);
                    }
                    break; // Found the UTR for this bank row, stop checking other UTRs
                }
            }

            if (!matchedAny) {
                // No known UTR found in this narration -> MISSING_IN_GATEWAY
                ExceptionQueue exception = ExceptionQueue.builder()
                        .reconciliationRunId(runId)
                        .matchId(UUID.randomUUID())
                        .matchType("B")
                        .exceptionType("MISSING_IN_GATEWAY")
                        .discrepancyAmount(bankRecord.getCreditAmount())
                        .currency("INR")
                        .status("OPEN")
                        .build();
                exceptionsToSave.add(exception);
            }
        }

        // Known UTRs that were never found in any bank row -> MISSING_IN_BANK
        for (Map.Entry<String, List<GatewaySettlement>> entry : groupedByUtr.entrySet()) {
            if (!matchedGatewayUtrs.contains(entry.getKey())) {
                List<GatewaySettlement> settlements = entry.getValue();
                BigDecimal sumNetAmount = settlements.stream()
                        .map(GatewaySettlement::getNetAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                        
                ExceptionQueue exception = ExceptionQueue.builder()
                        .reconciliationRunId(runId)
                        .matchId(settlements.get(0).getId())
                        .matchType("B")
                        .exceptionType("MISSING_IN_BANK")
                        .discrepancyAmount(sumNetAmount)
                        .currency("INR")
                        .status("OPEN")
                        .build();
                exceptionsToSave.add(exception);
            }
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
