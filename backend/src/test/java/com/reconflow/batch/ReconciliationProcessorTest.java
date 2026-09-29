package com.reconflow.batch;

import com.reconflow.dto.GatewaySettlementRecord;
import com.reconflow.dto.ReconciliationResult;
import com.reconflow.entity.LedgerTransaction;
import com.reconflow.repository.LedgerTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReconciliationProcessorTest {

    @Mock
    private LedgerTransactionRepository ledgerTransactionRepository;

    @InjectMocks
    private ReconciliationProcessor processor;

    private final String runId = UUID.randomUUID().toString();
    private final String fileId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(processor, "runIdStr", runId);
        ReflectionTestUtils.setField(processor, "uploadedFileIdStr", fileId);
    }

    @Test
    void testProcess_PerfectMatch() {
        // Arrange
        GatewaySettlementRecord record = GatewaySettlementRecord.builder()
                .gateway("RAZORPAY")
                .gatewayTransactionId("pay_123")
                .amount(new BigDecimal("100.00"))
                .transactionDate(ZonedDateTime.now())
                .build();

        LedgerTransaction ledger = LedgerTransaction.builder()
                .id(UUID.randomUUID())
                .gatewayTransactionId("pay_123")
                .amount(new BigDecimal("100.00"))
                .status("PENDING")
                .build();

        when(ledgerTransactionRepository.findByGatewayTransactionId("pay_123"))
                .thenReturn(Optional.of(ledger));

        // Act
        ReconciliationResult result = processor.process(record);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.hasException()).isFalse();
        assertThat(result.getGatewaySettlement()).isNotNull();
        assertThat(result.getGatewaySettlement().getGatewayTransactionId()).isEqualTo("pay_123");
        
        assertThat(result.getMatchedLedgerTransaction()).isNotNull();
        assertThat(result.getMatchedLedgerTransaction().getStatus()).isEqualTo("RECONCILED");
    }

    @Test
    void testProcess_MissingInLedger() {
        // Arrange
        GatewaySettlementRecord record = GatewaySettlementRecord.builder()
                .gateway("RAZORPAY")
                .gatewayTransactionId("pay_404")
                .amount(new BigDecimal("100.00"))
                .transactionDate(ZonedDateTime.now())
                .build();

        when(ledgerTransactionRepository.findByGatewayTransactionId("pay_404"))
                .thenReturn(Optional.empty());

        // Act
        ReconciliationResult result = processor.process(record);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.hasException()).isTrue();
        assertThat(result.getException().getExceptionType()).isEqualTo("MISSING_IN_LEDGER");
        assertThat(result.getException().getDiscrepancyAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(result.getMatchedLedgerTransaction()).isNull(); // No ledger matched
    }

    @Test
    void testProcess_AmountMismatch() {
        // Arrange
        GatewaySettlementRecord record = GatewaySettlementRecord.builder()
                .gateway("RAZORPAY")
                .gatewayTransactionId("pay_500")
                .amount(new BigDecimal("90.00")) // Gateway says 90
                .transactionDate(ZonedDateTime.now())
                .build();

        LedgerTransaction ledger = LedgerTransaction.builder()
                .id(UUID.randomUUID())
                .gatewayTransactionId("pay_500")
                .amount(new BigDecimal("100.00")) // We expected 100
                .currency("INR")
                .status("PENDING")
                .build();

        when(ledgerTransactionRepository.findByGatewayTransactionId("pay_500"))
                .thenReturn(Optional.of(ledger));

        // Act
        ReconciliationResult result = processor.process(record);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.hasException()).isTrue();
        assertThat(result.getException().getExceptionType()).isEqualTo("AMOUNT_MISMATCH");
        assertThat(result.getException().getDiscrepancyAmount()).isEqualByComparingTo(new BigDecimal("10.00"));
        
        assertThat(result.getMatchedLedgerTransaction()).isNotNull();
        assertThat(result.getMatchedLedgerTransaction().getStatus()).isEqualTo("EXCEPTION");
    }
}
