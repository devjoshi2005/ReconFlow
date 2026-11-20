package com.reconflow.batch;

import com.reconflow.dto.BankStatementRecord;
import com.reconflow.entity.ChainBMatch;
import com.reconflow.entity.ExceptionQueue;
import com.reconflow.entity.GatewaySettlement;
import com.reconflow.entity.UploadedFile;
import com.reconflow.repository.ChainBMatchRepository;
import com.reconflow.repository.ExceptionQueueRepository;
import com.reconflow.repository.GatewaySettlementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChainBTaskletTest {

    @Mock
    private CsvInMemoryLoader csvInMemoryLoader;
    @Mock
    private GatewaySettlementRepository gatewaySettlementRepository;
    @Mock
    private ChainBMatchRepository chainBMatchRepository;
    @Mock
    private ExceptionQueueRepository exceptionQueueRepository;
    @Mock
    private StepContribution stepContribution;
    @Mock
    private ChunkContext chunkContext;

    @InjectMocks
    private ChainBTasklet tasklet;

    private UUID runId;
    private LocalDate settlementDate;
    private UploadedFile currentFile;

    @BeforeEach
    void setUp() {
        runId = UUID.randomUUID();
        settlementDate = LocalDate.of(2026, 10, 1);
        currentFile = UploadedFile.builder()
                .id(UUID.randomUUID())
                .settlementDate(settlementDate)
                .build();
        ReflectionTestUtils.setField(tasklet, "runIdStr", runId.toString());
        when(csvInMemoryLoader.getCurrentFile()).thenReturn(currentFile);
    }

    @Test
    void skipsSettlementThatAlreadyHasMatchInThisRun() {
        GatewaySettlement settlement = settlement("100.00", "UTR-1");
        when(csvInMemoryLoader.getBankRecords()).thenReturn(bankRecords(bankRecord("UTR-1", "100.00")));
        when(gatewaySettlementRepository.findBySettlementDate(settlementDate)).thenReturn(List.of(settlement));
        when(chainBMatchRepository.findByReconciliationRunIdAndGatewaySettlementIdIn(
                runId, List.of(settlement.getId()))).thenReturn(List.of(existingMatch(settlement)));

        RepeatStatus status = tasklet.execute(stepContribution, chunkContext);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        verify(chainBMatchRepository, never()).saveAll(anyList());
        verify(exceptionQueueRepository, never()).saveAll(anyList());
    }

    @Test
    void createsMatchForNewSettlement() {
        GatewaySettlement settlement = settlement("100.00", "UTR-1");
        when(csvInMemoryLoader.getBankRecords()).thenReturn(bankRecords(bankRecord("UTR-1", "100.00")));
        when(gatewaySettlementRepository.findBySettlementDate(settlementDate)).thenReturn(List.of(settlement));
        when(chainBMatchRepository.findByReconciliationRunIdAndGatewaySettlementIdIn(
                runId, List.of(settlement.getId()))).thenReturn(List.of());

        RepeatStatus status = tasklet.execute(stepContribution, chunkContext);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        verify(chainBMatchRepository).saveAll(argThat(matches -> containsOnlyMatchFor(matches, settlement)));
        verify(exceptionQueueRepository, never()).saveAll(anyList());
    }

    @Test
    void alreadyMatchedUtrDoesNotSuppressAnotherBankRecord() {
        GatewaySettlement oldSettlement = settlement("100.00", "UTR-OLD");
        GatewaySettlement newSettlement = settlement("200.00", "UTR-NEW");
        when(csvInMemoryLoader.getBankRecords()).thenReturn(bankRecords(
                bankRecord("UTR-OLD", "100.00"), bankRecord("UTR-NEW", "200.00")));
        when(gatewaySettlementRepository.findBySettlementDate(settlementDate))
                .thenReturn(List.of(oldSettlement, newSettlement));
        when(chainBMatchRepository.findByReconciliationRunIdAndGatewaySettlementIdIn(
                eq(runId), anyList())).thenReturn(List.of(existingMatch(oldSettlement)));

        tasklet.execute(stepContribution, chunkContext);

        verify(chainBMatchRepository).saveAll(argThat(matches -> containsOnlyMatchFor(matches, newSettlement)));
        verify(exceptionQueueRepository, never()).saveAll(anyList());
    }

    @Test
    void skipsDuplicateNetAmountMismatchExceptionOnRetry() {
        GatewaySettlement settlement = settlement("100.00", "UTR-1");
        when(csvInMemoryLoader.getBankRecords()).thenReturn(bankRecords(bankRecord("UTR-1", "90.00")));
        when(gatewaySettlementRepository.findBySettlementDate(settlementDate)).thenReturn(List.of(settlement));
        when(chainBMatchRepository.findByReconciliationRunIdAndGatewaySettlementIdIn(
                runId, List.of(settlement.getId()))).thenReturn(List.of());
        when(exceptionQueueRepository.existsByReconciliationRunIdAndMatchIdAndExceptionType(
                runId, settlement.getId(), "NET_AMOUNT_MISMATCH")).thenReturn(true);

        tasklet.execute(stepContribution, chunkContext);

        verify(chainBMatchRepository, never()).saveAll(anyList());
        verify(exceptionQueueRepository, never()).saveAll(anyList());
    }

    private boolean containsOnlyMatchFor(Iterable<ChainBMatch> matches, GatewaySettlement settlement) {
        Iterator<ChainBMatch> iterator = matches.iterator();
        if (!iterator.hasNext()) {
            return false;
        }
        ChainBMatch match = iterator.next();
        return !iterator.hasNext() && settlement.getId().equals(match.getGatewaySettlementId());
    }

    private ChainBMatch existingMatch(GatewaySettlement settlement) {
        return ChainBMatch.builder()
                .id(UUID.randomUUID())
                .reconciliationRunId(runId)
                .gatewaySettlementId(settlement.getId())
                .bankStatementId(UUID.randomUUID())
                .matchStatus("MATCHED")
                .build();
    }

    private ConcurrentHashMap<String, BankStatementRecord> bankRecords(BankStatementRecord... records) {
        ConcurrentHashMap<String, BankStatementRecord> bankRecords = new ConcurrentHashMap<>();
        for (BankStatementRecord record : records) {
            bankRecords.put(record.getUtr(), record);
        }
        return bankRecords;
    }

    private BankStatementRecord bankRecord(String utr, String amount) {
        return BankStatementRecord.builder()
                .utr(utr)
                .narration("Razorpay Settlement " + utr)
                .creditAmount(new BigDecimal(amount))
                .build();
    }

    private GatewaySettlement settlement(String netAmount, String utr) {
        return GatewaySettlement.builder()
                .id(UUID.randomUUID())
                .uploadedFileId(currentFile.getId())
                .gateway("RAZORPAY")
                .gatewayTransactionId("pay_" + utr)
                .bankTransferRef(utr)
                .grossAmount(new BigDecimal(netAmount))
                .netAmount(new BigDecimal(netAmount))
                .feeAmount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .settlementDate(settlementDate)
                .build();
    }
}
