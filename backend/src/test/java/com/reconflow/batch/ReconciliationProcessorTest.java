package com.reconflow.batch;

import com.reconflow.dto.GatewaySettlementRecord;
import com.reconflow.dto.ReconciliationResult;
import com.reconflow.entity.ChainAMatch;
import com.reconflow.entity.ExceptionQueue;
import com.reconflow.entity.GatewaySettlement;
import com.reconflow.entity.LedgerTransaction;
import com.reconflow.entity.UploadedFile;
import com.reconflow.entity.status.ExceptionStatus;
import com.reconflow.entity.status.LedgerTransactionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ReconciliationProcessorTest {

    private static final UUID RUN_ID = UUID.randomUUID();
    private static final UUID FILE_ID = UUID.randomUUID();
    private static final LocalDate SETTLEMENT_DATE = LocalDate.of(2026, 11, 20);
    private static final ZonedDateTime GATEWAY_TRANSACTION_DATE =
            ZonedDateTime.of(2026, 11, 20, 10, 30, 0, 0, ZoneOffset.UTC);

    @Mock
    private CsvInMemoryLoader csvInMemoryLoader;

    @InjectMocks
    private ReconciliationProcessor processor;

    private ConcurrentHashMap<String, GatewaySettlementRecord> gatewayRecords;
    private UploadedFile currentFile;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(processor, "runIdStr", RUN_ID.toString());
        ReflectionTestUtils.setField(processor, "uploadedFileIdStr", FILE_ID.toString());

        gatewayRecords = new ConcurrentHashMap<>();
        currentFile = UploadedFile.builder()
                .id(FILE_ID)
                .sourceType("GATEWAY")
                .gateway("RAZORPAY")
                .settlementDate(SETTLEMENT_DATE)
                .build();

        lenient().when(csvInMemoryLoader.getGatewayRecords()).thenReturn(gatewayRecords);
        lenient().when(csvInMemoryLoader.getCurrentFile()).thenReturn(currentFile);
    }

    static Stream<Arguments> ledgerStateAndGatewayOutcome() {
        return Stream.of(
                // A first-time PENDING row can reconcile or become UNRECONCILED.
                Arguments.of(LedgerTransactionStatus.PENDING, "100.00",
                        LedgerTransactionStatus.RECONCILED, false),
                Arguments.of(LedgerTransactionStatus.PENDING, "90.00",
                        LedgerTransactionStatus.UNRECONCILED, true),
                // The reader deliberately re-reads UNRECONCILED rows, so a corrected
                // gateway amount must be able to reconcile them.
                Arguments.of(LedgerTransactionStatus.UNRECONCILED, "100.00",
                        LedgerTransactionStatus.RECONCILED, false),
                // Re-processing the same mismatch must be idempotent, not throw.
                Arguments.of(LedgerTransactionStatus.UNRECONCILED, "90.00",
                        LedgerTransactionStatus.UNRECONCILED, true)
        );
    }

    @ParameterizedTest(name = "{0} + gateway {1} -> {2} (exception={3})")
    @MethodSource("ledgerStateAndGatewayOutcome")
    void processesEveryValidLedgerStateIdempotently(
            LedgerTransactionStatus initialStatus,
            String gatewayAmount,
            LedgerTransactionStatus expectedStatus,
            boolean expectsException) {

        String txnId = "pay-" + initialStatus + "-" + gatewayAmount;
        gatewayRecords.put(txnId, gatewayRecord(txnId, gatewayAmount,
                new BigDecimal("1.00"), new BigDecimal("0.18")));
        LedgerTransaction ledger = ledger(txnId, "100.00", initialStatus);

        ReconciliationResult result = processor.process(ledger);

        assertThat(result).isNotNull();
        assertThat(result.getMatchedLedgerTransaction()).isSameAs(ledger);
        assertThat(ledger.getStatus()).isEqualTo(expectedStatus);
        assertThat(result.hasException()).isEqualTo(expectsException);
        // A gateway row is consumed by every successful lookup.
        assertThat(gatewayRecords).doesNotContainKey(txnId);

        if (expectsException) {
            assertThat(result.getException().getExceptionType()).isEqualTo("AMOUNT_MISMATCH");
            assertThat(result.getException().getDiscrepancyAmount())
                    .isEqualByComparingTo(new BigDecimal("10.00"));
            assertThat(result.getChainAMatch()).isNull();
        } else {
            assertThat(result.getException()).isNull();
            assertThat(result.getChainAMatch()).isNotNull();
        }
    }

    @Test
    void missingGatewayRecordReturnsNullAndLeavesLedgerPending() {
        LedgerTransaction ledger = ledger("pay-not-in-gateway", "100.00", LedgerTransactionStatus.PENDING);

        ReconciliationResult result = processor.process(ledger);

        assertThat(result).isNull();
        assertThat(ledger.getStatus()).isEqualTo(LedgerTransactionStatus.PENDING);
        assertThat(gatewayRecords).isEmpty();
    }

    @Test
    void buildsSettlementFromEveryGatewayField() {
        GatewaySettlementRecord gateway = gatewayRecord("pay-1", "100.00",
                new BigDecimal("1.00"), new BigDecimal("0.18"));
        gatewayRecords.put("pay-1", gateway);
        LedgerTransaction ledger = ledger("pay-1", "100.00", LedgerTransactionStatus.PENDING);

        GatewaySettlement settlement = processor.process(ledger).getGatewaySettlement();

        assertThat(settlement.getUploadedFileId()).isEqualTo(FILE_ID);
        assertThat(settlement.getGateway()).isEqualTo("RAZORPAY");
        assertThat(settlement.getGatewayTransactionId()).isEqualTo("pay-1");
        assertThat(settlement.getOrderId()).isEqualTo("ORD-pay-1");
        assertThat(settlement.getSettlementBatchId()).isEqualTo("SETTLE-pay-1");
        assertThat(settlement.getGrossAmount()).isEqualByComparingTo("100.00");
        assertThat(settlement.getFeeAmount()).isEqualByComparingTo("1.00");
        assertThat(settlement.getTaxAmount()).isEqualByComparingTo("0.18");
        assertThat(settlement.getNetAmount()).isEqualByComparingTo("98.82");
        assertThat(settlement.getBankTransferRef()).isEqualTo("UTR-pay-1");
        assertThat(settlement.getSettlementDate()).isEqualTo(SETTLEMENT_DATE);
    }

    @Test
    void fallsBackToGatewayTransactionDateWhenFileHasNoSettlementDate() {
        currentFile.setSettlementDate(null);
        gatewayRecords.put("pay-2", gatewayRecord("pay-2", "100.00",
                new BigDecimal("1.00"), new BigDecimal("0.18")));
        LedgerTransaction ledger = ledger("pay-2", "100.00", LedgerTransactionStatus.PENDING);

        GatewaySettlement settlement = processor.process(ledger).getGatewaySettlement();

        assertThat(settlement.getSettlementDate()).isEqualTo(GATEWAY_TRANSACTION_DATE.toLocalDate());
    }

    @Test
    void treatsMissingFeeAndTaxAsZero() {
        gatewayRecords.put("pay-3", gatewayRecord("pay-3", "100.00", null, null));
        LedgerTransaction ledger = ledger("pay-3", "100.00", LedgerTransactionStatus.PENDING);

        GatewaySettlement settlement = processor.process(ledger).getGatewaySettlement();

        assertThat(settlement.getFeeAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(settlement.getTaxAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(settlement.getNetAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void mismatchExceptionCarriesRunLedgerAndGatewayMetadata() {
        gatewayRecords.put("pay-4", gatewayRecord("pay-4", "90.00",
                new BigDecimal("1.00"), new BigDecimal("0.18")));
        LedgerTransaction ledger = ledger("pay-4", "100.00", LedgerTransactionStatus.PENDING);

        ExceptionQueue exception = processor.process(ledger).getException();

        assertThat(exception.getReconciliationRunId()).isEqualTo(RUN_ID);
        assertThat(exception.getMatchId()).isEqualTo(ledger.getId());
        assertThat(exception.getMatchType()).isEqualTo("A");
        assertThat(exception.getExceptionType()).isEqualTo("AMOUNT_MISMATCH");
        assertThat(exception.getDiscrepancyAmount()).isEqualByComparingTo("10.00");
        assertThat(exception.getCurrency()).isEqualTo("INR");
        assertThat(exception.getGateway()).isEqualTo("RAZORPAY");
        assertThat(exception.getStatus()).isEqualTo(ExceptionStatus.OPEN);
    }

    @Test
    void matchCarriesRunAndLedgerMetadataAndLeavesSettlementIdToTheWriter() {
        gatewayRecords.put("pay-5", gatewayRecord("pay-5", "100.00",
                new BigDecimal("1.00"), new BigDecimal("0.18")));
        LedgerTransaction ledger = ledger("pay-5", "100.00", LedgerTransactionStatus.PENDING);

        ChainAMatch match = processor.process(ledger).getChainAMatch();

        assertThat(match.getReconciliationRunId()).isEqualTo(RUN_ID);
        assertThat(match.getLedgerTransactionId()).isEqualTo(ledger.getId());
        assertThat(match.getGatewaySettlementId()).isNull();
        assertThat(match.getMatchStatus()).isEqualTo("MATCHED");
    }

    private static LedgerTransaction ledger(String gatewayTransactionId, String amount,
                                            LedgerTransactionStatus status) {
        return LedgerTransaction.builder()
                .id(UUID.randomUUID())
                .gatewayTransactionId(gatewayTransactionId)
                .amount(new BigDecimal(amount))
                .currency("INR")
                .gateway("RAZORPAY")
                .status(status)
                .build();
    }

    private static GatewaySettlementRecord gatewayRecord(String gatewayTransactionId, String amount,
                                                         BigDecimal fee, BigDecimal tax) {
        return GatewaySettlementRecord.builder()
                .gateway("RAZORPAY")
                .gatewayTransactionId(gatewayTransactionId)
                .orderId("ORD-" + gatewayTransactionId)
                .settlementId("SETTLE-" + gatewayTransactionId)
                .amount(new BigDecimal(amount))
                .fee(fee)
                .tax(tax)
                .bankTransferRef("UTR-" + gatewayTransactionId)
                .transactionDate(GATEWAY_TRANSACTION_DATE)
                .build();
    }
}
