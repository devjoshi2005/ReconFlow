package com.reconflow.batch;

import com.reconflow.dto.ReconciliationResult;
import com.reconflow.entity.ChainAMatch;
import com.reconflow.entity.ExceptionQueue;
import com.reconflow.entity.GatewaySettlement;
import com.reconflow.entity.LedgerTransaction;
import com.reconflow.repository.ChainAMatchRepository;
import com.reconflow.repository.ExceptionQueueRepository;
import com.reconflow.repository.GatewaySettlementRepository;
import com.reconflow.repository.LedgerTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReconciliationWriter implements ItemWriter<ReconciliationResult> {

    private final GatewaySettlementRepository gatewaySettlementRepository;
    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final ExceptionQueueRepository exceptionQueueRepository;
    private final ChainAMatchRepository chainAMatchRepository;

    @Override
    public void write(Chunk<? extends ReconciliationResult> chunk) {
        List<ReconciliationResult> results = new ArrayList<>(chunk.getItems());
        Map<String, GatewaySettlement> settlements = saveSettlements(results);

        saveLedgers(results);
        saveMatches(results, settlements);
        saveExceptions(results);
    }

    private Map<String, GatewaySettlement> saveSettlements(List<ReconciliationResult> results) {
        Map<String, GatewaySettlement> candidates = new LinkedHashMap<>();
        for (ReconciliationResult result : results) {
            GatewaySettlement settlement = result.getGatewaySettlement();
            if (settlement != null) {
                candidates.putIfAbsent(settlement.getGatewayTransactionId(), settlement);
            }
        }
        if (candidates.isEmpty()) {
            return Map.of();
        }

        UUID fileId = candidates.values().iterator().next().getUploadedFileId();
        if (candidates.values().stream().anyMatch(settlement -> !fileId.equals(settlement.getUploadedFileId()))) {
            throw new IllegalArgumentException("A reconciliation chunk cannot contain multiple uploaded files");
        }

        Map<String, GatewaySettlement> persisted = new HashMap<>();
        gatewaySettlementRepository
                .findByUploadedFileIdAndGatewayTransactionIdIn(fileId, List.copyOf(candidates.keySet()))
                .forEach(settlement -> persisted.put(settlement.getGatewayTransactionId(), settlement));

        List<GatewaySettlement> newSettlements = candidates.values().stream()
                .filter(settlement -> !persisted.containsKey(settlement.getGatewayTransactionId()))
                .toList();
        if (!newSettlements.isEmpty()) {
            gatewaySettlementRepository.saveAll(newSettlements)
                    .forEach(settlement -> persisted.put(settlement.getGatewayTransactionId(), settlement));
            log.info("Saved {} gateway settlements.", newSettlements.size());
        }
        return persisted;
    }

    private void saveLedgers(List<ReconciliationResult> results) {
        List<LedgerTransaction> ledgers = results.stream()
                .map(ReconciliationResult::getMatchedLedgerTransaction)
                .filter(Objects::nonNull)
                .toList();
        if (!ledgers.isEmpty()) {
            ledgerTransactionRepository.saveAll(ledgers);
            log.info("Updated {} ledger transactions.", ledgers.size());
        }
    }

    private void saveMatches(List<ReconciliationResult> results, Map<String, GatewaySettlement> settlements) {
        List<ChainAMatch> matches = new ArrayList<>();
        for (ReconciliationResult result : results) {
            ChainAMatch match = result.getChainAMatch();
            if (match == null || match.getLedgerTransactionId() == null) {
                continue;
            }
            if (result.getGatewaySettlement() != null) {
                String transactionId = result.getGatewaySettlement().getGatewayTransactionId();
                GatewaySettlement settlement = settlements.get(transactionId);
                if (settlement == null) {
                    throw new IllegalStateException("Gateway settlement was not persisted before linking Chain A match");
                }
                match.setGatewaySettlementId(settlement.getId());
            }
            matches.add(match);
        }
        if (matches.isEmpty()) {
            return;
        }

        UUID runId = matches.get(0).getReconciliationRunId();
        List<UUID> ledgerIds = matches.stream()
                .map(ChainAMatch::getLedgerTransactionId)
                .distinct()
                .toList();
        Set<UUID> existingLedgerIds = chainAMatchRepository
                .findByReconciliationRunIdAndLedgerTransactionIdIn(runId, ledgerIds)
                .stream()
                .map(ChainAMatch::getLedgerTransactionId)
                .collect(Collectors.toSet());

        List<ChainAMatch> newMatches = matches.stream()
                .filter(match -> !existingLedgerIds.contains(match.getLedgerTransactionId()))
                .toList();
        if (!newMatches.isEmpty()) {
            chainAMatchRepository.saveAll(newMatches);
            log.info("Saved {} Chain A matches.", newMatches.size());
        }
    }

    private void saveExceptions(List<ReconciliationResult> results) {
        List<ExceptionQueue> exceptions = results.stream()
                .filter(ReconciliationResult::hasException)
                .map(ReconciliationResult::getException)
                .filter(this::isNew)
                .toList();
        if (!exceptions.isEmpty()) {
            exceptionQueueRepository.saveAll(exceptions);
            log.warn("Generated {} exceptions.", exceptions.size());
        }
    }

    private boolean isNew(ExceptionQueue exception) {
        return !exceptionQueueRepository.existsByReconciliationRunIdAndMatchIdAndExceptionType(
                exception.getReconciliationRunId(), exception.getMatchId(), exception.getExceptionType());
    }
}
