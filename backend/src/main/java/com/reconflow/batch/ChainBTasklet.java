package com.reconflow.batch;

import com.reconflow.dto.BankStatementRecord;
import com.reconflow.entity.ChainBMatch;
import com.reconflow.entity.ExceptionQueue;
import com.reconflow.entity.GatewaySettlement;
import com.reconflow.entity.status.ExceptionStatus;
import com.reconflow.repository.ChainBMatchRepository;
import com.reconflow.repository.ExceptionQueueRepository;
import com.reconflow.repository.GatewaySettlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
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
        Map<String, BankStatementRecord> bankRecords = csvInMemoryLoader.getBankRecords();
        LocalDate settlementDate = csvInMemoryLoader.getCurrentFile().getSettlementDate();
        List<GatewaySettlement> allSettlements = gatewaySettlementRepository.findBySettlementDate(settlementDate);
        Set<UUID> alreadyMatchedIds = findAlreadyMatchedIds(runId, allSettlements);

        Map<String, List<GatewaySettlement>> settlementsByUtr = allSettlements.stream()
                .filter(settlement -> settlement.getBankTransferRef() != null
                        && !settlement.getBankTransferRef().isEmpty())
                .collect(Collectors.groupingBy(GatewaySettlement::getBankTransferRef));

        List<ChainBMatch> matches = new ArrayList<>();
        List<ExceptionQueue> exceptions = new ArrayList<>();
        Set<String> matchedUtrs = new HashSet<>();

        log.info("Starting Chain B with {} bank records and {} settlements ({} already matched).",
                bankRecords.size(), allSettlements.size(), alreadyMatchedIds.size());

        for (BankStatementRecord bankRecord : bankRecords.values()) {
            String narration = bankRecord.getNarration() != null ? bankRecord.getNarration() : "";
            boolean matchedAny = false;

            for (Map.Entry<String, List<GatewaySettlement>> entry : settlementsByUtr.entrySet()) {
                String utr = entry.getKey();
                if (!narration.contains(utr)) {
                    continue;
                }

                matchedAny = true;
                matchedUtrs.add(utr);
                List<GatewaySettlement> pending = pendingSettlements(entry.getValue(), alreadyMatchedIds);
                if (pending.isEmpty()) {
                    break;
                }

                BigDecimal netAmount = totalNetAmount(pending);
                if (netAmount.compareTo(bankRecord.getCreditAmount()) != 0) {
                    BigDecimal discrepancy = netAmount.subtract(bankRecord.getCreditAmount()).abs();
                    exceptions.add(exception(runId, pending.get(0), "NET_AMOUNT_MISMATCH", discrepancy));
                } else {
                    matches.add(ChainBMatch.builder()
                            .reconciliationRunId(runId)
                            .gatewaySettlementId(pending.get(0).getId())
                            .bankStatementId(UUID.randomUUID())
                            .matchStatus("MATCHED")
                            .build());
                }
                break;
            }

            if (!matchedAny) {
                exceptions.add(missingInGatewayException(runId, bankRecord));
            }
        }

        for (Map.Entry<String, List<GatewaySettlement>> entry : settlementsByUtr.entrySet()) {
            if (matchedUtrs.contains(entry.getKey())) {
                continue;
            }
            List<GatewaySettlement> pending = pendingSettlements(entry.getValue(), alreadyMatchedIds);
            if (!pending.isEmpty()) {
                exceptions.add(exception(runId, pending.get(0), "MISSING_IN_BANK", totalNetAmount(pending)));
            }
        }

        if (!matches.isEmpty()) {
            chainBMatchRepository.saveAll(matches);
            log.info("Saved {} Chain B matches.", matches.size());
        }

        List<ExceptionQueue> newExceptions = exceptions.stream()
                .filter(this::isNew)
                .toList();
        if (!newExceptions.isEmpty()) {
            exceptionQueueRepository.saveAll(newExceptions);
            log.info("Saved {} Chain B exceptions.", newExceptions.size());
        }
        return RepeatStatus.FINISHED;
    }

    private Set<UUID> findAlreadyMatchedIds(UUID runId, List<GatewaySettlement> settlements) {
        if (settlements.isEmpty()) {
            return Set.of();
        }
        List<UUID> settlementIds = settlements.stream()
                .map(GatewaySettlement::getId)
                .toList();
        return chainBMatchRepository.findByReconciliationRunIdAndGatewaySettlementIdIn(runId, settlementIds)
                .stream()
                .map(ChainBMatch::getGatewaySettlementId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private List<GatewaySettlement> pendingSettlements(List<GatewaySettlement> settlements, Set<UUID> matchedIds) {
        return settlements.stream()
                .filter(settlement -> !matchedIds.contains(settlement.getId()))
                .toList();
    }

    private BigDecimal totalNetAmount(List<GatewaySettlement> settlements) {
        return settlements.stream()
                .map(GatewaySettlement::getNetAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private ExceptionQueue missingInGatewayException(UUID runId, BankStatementRecord bankRecord) {
        String narration = bankRecord.getNarration() != null ? bankRecord.getNarration() : "";
        String upperNarration = narration.toUpperCase();
        String gateway = "UNKNOWN";
        if (upperNarration.contains("RAZORPAY") || upperNarration.contains("RZP")) {
            gateway = "RAZORPAY";
        } else if (upperNarration.contains("PHONEPE") || upperNarration.contains("PHPE")) {
            gateway = "PHONEPE";
        } else if (upperNarration.contains("PAYU")) {
            gateway = "PAYU";
        }

        String reference = bankRecord.getUtr() != null ? bankRecord.getUtr() : narration;
        UUID matchId = UUID.nameUUIDFromBytes((runId + "|" + reference).getBytes(StandardCharsets.UTF_8));
        return exception(runId, matchId, gateway, "MISSING_IN_GATEWAY", bankRecord.getCreditAmount());
    }

    private ExceptionQueue exception(UUID runId, GatewaySettlement settlement,
                                     String exceptionType, BigDecimal discrepancy) {
        return exception(runId, settlement.getId(), settlement.getGateway(), exceptionType, discrepancy);
    }

    private ExceptionQueue exception(UUID runId, UUID matchId, String gateway,
                                     String exceptionType, BigDecimal discrepancy) {
        return ExceptionQueue.builder()
                .reconciliationRunId(runId)
                .matchId(matchId)
                .matchType("B")
                .exceptionType(exceptionType)
                .discrepancyAmount(discrepancy)
                .currency("INR")
                .status(ExceptionStatus.OPEN)
                .gateway(gateway)
                .build();
    }

    private boolean isNew(ExceptionQueue exception) {
        return !exceptionQueueRepository.existsByReconciliationRunIdAndMatchIdAndExceptionType(
                exception.getReconciliationRunId(), exception.getMatchId(), exception.getExceptionType());
    }
}
