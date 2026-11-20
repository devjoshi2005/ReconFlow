package com.reconflow.batch;

import com.reconflow.dto.GatewaySettlementRecord;
import com.reconflow.entity.ExceptionQueue;
import com.reconflow.entity.GatewaySettlement;
import com.reconflow.entity.UploadedFile;
import com.reconflow.repository.ExceptionQueueRepository;
import com.reconflow.repository.GatewaySettlementRepository;
import com.reconflow.repository.UploadedFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CsvInMemoryLoaderTest {

    @Mock
    private S3Client s3Client;
    @Mock
    private UploadedFileRepository uploadedFileRepository;
    @Mock
    private CsvHeaderMapper headerMapper;
    @Mock
    private ExceptionQueueRepository exceptionQueueRepository;
    @Mock
    private GatewaySettlementRepository gatewaySettlementRepository;

    private CsvInMemoryLoader loader;
    private UUID runId;
    private UploadedFile currentFile;

    @BeforeEach
    void setUp() {
        loader = new CsvInMemoryLoader(s3Client, uploadedFileRepository, List.of(), headerMapper,
                exceptionQueueRepository, gatewaySettlementRepository);
        runId = UUID.randomUUID();
        currentFile = UploadedFile.builder()
                .id(UUID.randomUUID())
                .sourceType("GATEWAY")
                .settlementDate(LocalDate.of(2026, 10, 1))
                .build();
        ReflectionTestUtils.setField(loader, "currentFile", currentFile);
        ReflectionTestUtils.setField(loader, "runIdStr", runId.toString());
        loader.getGatewayRecords().put("pay_1", GatewaySettlementRecord.builder()
                .gateway("RAZORPAY")
                .gatewayTransactionId("pay_1")
                .amount(new BigDecimal("100.00"))
                .fee(new BigDecimal("2.00"))
                .tax(new BigDecimal("1.00"))
                .bankTransferRef("UTR-1")
                .transactionDate(ZonedDateTime.parse("2026-10-01T10:00:00Z"))
                .build());
    }

    @Test
    void savesSettlementAndUsesItsPersistedIdForException() {
        UUID settlementId = UUID.randomUUID();
        when(gatewaySettlementRepository.findByUploadedFileIdAndGatewayTransactionIdIn(
                currentFile.getId(), List.of("pay_1"))).thenReturn(List.of());
        when(gatewaySettlementRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<GatewaySettlement> settlements = invocation.getArgument(0);
            settlements.forEach(settlement -> settlement.setId(settlementId));
            return settlements;
        });

        loader.afterStep(null);

        verify(gatewaySettlementRepository).saveAll(anyList());
        verify(exceptionQueueRepository).saveAll(argThat(exceptions ->
                containsOnlyExceptionWithMatchId(exceptions, settlementId)));
    }

    @Test
    void reusesExistingSettlementAndWritesExceptionOnlyOnceAcrossRetries() {
        GatewaySettlement existing = GatewaySettlement.builder()
                .id(UUID.randomUUID())
                .uploadedFileId(currentFile.getId())
                .gateway("RAZORPAY")
                .gatewayTransactionId("pay_1")
                .grossAmount(new BigDecimal("100.00"))
                .netAmount(new BigDecimal("97.00"))
                .settlementDate(currentFile.getSettlementDate())
                .build();
        when(gatewaySettlementRepository.findByUploadedFileIdAndGatewayTransactionIdIn(
                currentFile.getId(), List.of("pay_1"))).thenReturn(List.of(existing));
        when(exceptionQueueRepository.existsByReconciliationRunIdAndMatchIdAndExceptionType(
                runId, existing.getId(), "MISSING_IN_LEDGER")).thenReturn(false, true);

        loader.afterStep(null);
        loader.afterStep(null);

        verify(gatewaySettlementRepository, never()).saveAll(anyList());
        verify(exceptionQueueRepository, times(1)).saveAll(argThat(exceptions ->
                containsOnlyExceptionWithMatchId(exceptions, existing.getId())));
    }

    private boolean containsOnlyExceptionWithMatchId(Iterable<ExceptionQueue> exceptions, UUID matchId) {
        Iterator<ExceptionQueue> iterator = exceptions.iterator();
        if (!iterator.hasNext()) {
            return false;
        }
        ExceptionQueue exception = iterator.next();
        return !iterator.hasNext() && matchId.equals(exception.getMatchId());
    }
}
