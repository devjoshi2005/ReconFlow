package com.reconflow.batch;

import com.reconflow.dto.ReconciliationResult;
import com.reconflow.entity.ChainAMatch;
import com.reconflow.entity.ExceptionQueue;
import com.reconflow.entity.GatewaySettlement;
import com.reconflow.entity.LedgerTransaction;
import com.reconflow.entity.status.ExceptionStatus;
import com.reconflow.repository.ChainAMatchRepository;
import com.reconflow.repository.ExceptionQueueRepository;
import com.reconflow.repository.GatewaySettlementRepository;
import com.reconflow.repository.LedgerTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.item.Chunk;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReconciliationWriterTest {

    @Mock
    private GatewaySettlementRepository gatewaySettlementRepository;
    @Mock
    private LedgerTransactionRepository ledgerTransactionRepository;
    @Mock
    private ExceptionQueueRepository exceptionQueueRepository;
    @Mock
    private ChainAMatchRepository chainAMatchRepository;

    @InjectMocks
    private ReconciliationWriter writer;

    @Test
    void reusesExistingSettlementAndSkipsExistingMatch() {
        UUID fileId = UUID.randomUUID();
        UUID ledgerId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        GatewaySettlement existing = settlement(fileId, "pay_1");
        existing.setId(UUID.randomUUID());
        GatewaySettlement incoming = settlement(fileId, "pay_1");
        ChainAMatch match = match(runId, ledgerId);
        ReconciliationResult result = ReconciliationResult.builder()
                .gatewaySettlement(incoming)
                .matchedLedgerTransaction(LedgerTransaction.builder().build())
                .chainAMatch(match)
                .build();

        when(gatewaySettlementRepository.findByUploadedFileIdAndGatewayTransactionIdIn(fileId, List.of("pay_1")))
                .thenReturn(List.of(existing));
        when(chainAMatchRepository.findByReconciliationRunIdAndLedgerTransactionIdIn(runId, List.of(ledgerId)))
                .thenReturn(List.of(ChainAMatch.builder()
                        .reconciliationRunId(runId)
                        .ledgerTransactionId(ledgerId)
                        .build()));

        writer.write(new Chunk<>(List.of(result)));

        verify(gatewaySettlementRepository, never()).saveAll(anyList());
        verify(chainAMatchRepository, never()).saveAll(anyList());
        assertThat(match.getGatewaySettlementId()).isEqualTo(existing.getId());
    }

    @Test
    void savesNewSettlementAndLinksMatchToPersistedId() {
        UUID fileId = UUID.randomUUID();
        UUID ledgerId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID settlementId = UUID.randomUUID();
        GatewaySettlement settlement = settlement(fileId, "pay_2");
        ChainAMatch match = match(runId, ledgerId);
        ReconciliationResult result = ReconciliationResult.builder()
                .gatewaySettlement(settlement)
                .matchedLedgerTransaction(LedgerTransaction.builder().build())
                .chainAMatch(match)
                .build();

        when(gatewaySettlementRepository.findByUploadedFileIdAndGatewayTransactionIdIn(fileId, List.of("pay_2")))
                .thenReturn(List.of());
        when(gatewaySettlementRepository.saveAll(List.of(settlement))).thenAnswer(invocation -> {
            settlement.setId(settlementId);
            return List.of(settlement);
        });
        when(chainAMatchRepository.findByReconciliationRunIdAndLedgerTransactionIdIn(runId, List.of(ledgerId)))
                .thenReturn(List.of());

        writer.write(new Chunk<>(List.of(result)));

        verify(gatewaySettlementRepository).saveAll(List.of(settlement));
        verify(chainAMatchRepository).saveAll(List.of(match));
        assertThat(match.getGatewaySettlementId()).isEqualTo(settlementId);
    }

    @Test
    void skipsDuplicateSettlementWithinSameChunk() {
        UUID fileId = UUID.randomUUID();
        GatewaySettlement settlement = settlement(fileId, "pay_1");
        when(gatewaySettlementRepository.findByUploadedFileIdAndGatewayTransactionIdIn(fileId, List.of("pay_1")))
                .thenReturn(List.of());
        when(gatewaySettlementRepository.saveAll(List.of(settlement))).thenReturn(List.of(settlement));

        writer.write(new Chunk<>(List.of(result(settlement), result(settlement))));

        verify(gatewaySettlementRepository).saveAll(List.of(settlement));
    }

    @Test
    void skipsDuplicateExceptionForSameRunMatchAndType() {
        UUID runId = UUID.randomUUID();
        UUID ledgerId = UUID.randomUUID();
        ExceptionQueue exception = ExceptionQueue.builder()
                .reconciliationRunId(runId)
                .matchId(ledgerId)
                .matchType("A")
                .exceptionType("AMOUNT_MISMATCH")
                .discrepancyAmount(BigDecimal.TEN)
                .currency("INR")
                .status(ExceptionStatus.OPEN)
                .build();

        when(exceptionQueueRepository.existsByReconciliationRunIdAndMatchIdAndExceptionType(
                runId, ledgerId, "AMOUNT_MISMATCH")).thenReturn(true);

        writer.write(new Chunk<>(List.of(ReconciliationResult.builder().exception(exception).build())));

        verify(exceptionQueueRepository, never()).saveAll(anyList());
    }

    private ReconciliationResult result(GatewaySettlement settlement) {
        return ReconciliationResult.builder().gatewaySettlement(settlement).build();
    }

    private ChainAMatch match(UUID runId, UUID ledgerId) {
        return ChainAMatch.builder()
                .reconciliationRunId(runId)
                .ledgerTransactionId(ledgerId)
                .matchStatus("MATCHED")
                .build();
    }

    private GatewaySettlement settlement(UUID fileId, String transactionId) {
        return GatewaySettlement.builder()
                .uploadedFileId(fileId)
                .gateway("RAZORPAY")
                .gatewayTransactionId(transactionId)
                .grossAmount(new BigDecimal("100.00"))
                .netAmount(new BigDecimal("100.00"))
                .settlementDate(LocalDate.of(2026, 10, 1))
                .build();
    }
}
