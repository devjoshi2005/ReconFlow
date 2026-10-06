package com.reconflow.batch;

import com.reconflow.dto.GatewaySettlementRecord;
import com.reconflow.dto.ReconciliationResult;
import com.reconflow.entity.LedgerTransaction;
import com.reconflow.entity.UploadedFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReconciliationProcessorTest {

    @Mock
    private CsvInMemoryLoader csvInMemoryLoader;

    @InjectMocks
    private ReconciliationProcessor processor;

    private final String runId = UUID.randomUUID().toString();
    private final String fileId = UUID.randomUUID().toString();
    private ConcurrentHashMap<String, GatewaySettlementRecord> gatewayRecords;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(processor, "runIdStr", runId);
        ReflectionTestUtils.setField(processor, "uploadedFileIdStr", fileId);
        gatewayRecords = new ConcurrentHashMap<>();
    }

    @Test
    void testProcess_PerfectMatch() {
        // Arrange
        GatewaySettlementRecord gatewayRecord = GatewaySettlementRecord.builder()
                .gateway("RAZORPAY")
                .gatewayTransactionId("pay_123")
                .amount(new BigDecimal("100.00"))
                .transactionDate(ZonedDateTime.now())
                .build();
        gatewayRecords.put("pay_123", gatewayRecord);

        UploadedFile uploadedFile = UploadedFile.builder().id(UUID.randomUUID()).build();
        when(csvInMemoryLoader.getGatewayRecords()).thenReturn(gatewayRecords);
        when(csvInMemoryLoader.getCurrentFile()).thenReturn(uploadedFile);

        LedgerTransaction ledger = LedgerTransaction.builder()
                .id(UUID.randomUUID())
                .gatewayTransactionId("pay_123")
                .amount(new BigDecimal("100.00"))
                .status("PENDING")
                .build();

        // Act
        ReconciliationResult result = processor.process(ledger);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.hasException()).isFalse();
        assertThat(result.getGatewaySettlement()).isNotNull();
        assertThat(result.getGatewaySettlement().getGatewayTransactionId()).isEqualTo("pay_123");
        
        assertThat(result.getMatchedLedgerTransaction()).isNotNull();
        assertThat(result.getMatchedLedgerTransaction().getStatus()).isEqualTo("RECONCILED");
        assertThat(gatewayRecords).isEmpty(); // It should be removed from the map
    }

    @Test
    void testProcess_MissingInGateway() {
        // Arrange
        when(csvInMemoryLoader.getGatewayRecords()).thenReturn(gatewayRecords);

        LedgerTransaction ledger = LedgerTransaction.builder()
                .id(UUID.randomUUID())
                .gatewayTransactionId("pay_404")
                .amount(new BigDecimal("100.00"))
                .status("PENDING")
                .build();

        // Act
        ReconciliationResult result = processor.process(ledger);

        // Assert
        assertThat(result).isNull();
    }

    @Test
    void testProcess_AmountMismatch() {
        // Arrange
        GatewaySettlementRecord gatewayRecord = GatewaySettlementRecord.builder()
                .gateway("RAZORPAY")
                .gatewayTransactionId("pay_500")
                .amount(new BigDecimal("90.00")) // Gateway says 90
                .transactionDate(ZonedDateTime.now())
                .build();
        gatewayRecords.put("pay_500", gatewayRecord);

        UploadedFile uploadedFile = UploadedFile.builder().id(UUID.randomUUID()).build();
        when(csvInMemoryLoader.getGatewayRecords()).thenReturn(gatewayRecords);
        when(csvInMemoryLoader.getCurrentFile()).thenReturn(uploadedFile);

        LedgerTransaction ledger = LedgerTransaction.builder()
                .id(UUID.randomUUID())
                .gatewayTransactionId("pay_500")
                .amount(new BigDecimal("100.00")) // We expected 100
                .currency("INR")
                .status("PENDING")
                .build();

        // Act
        ReconciliationResult result = processor.process(ledger);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.hasException()).isTrue();
        assertThat(result.getException().getExceptionType()).isEqualTo("AMOUNT_MISMATCH");
        assertThat(result.getException().getDiscrepancyAmount()).isEqualByComparingTo(new BigDecimal("10.00"));
        
        assertThat(result.getMatchedLedgerTransaction()).isNotNull();
        assertThat(result.getMatchedLedgerTransaction().getStatus()).isEqualTo("UNRECONCILED");
    }
}
