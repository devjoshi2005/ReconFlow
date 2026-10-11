package com.reconflow;

import com.reconflow.entity.LedgerTransaction;
import com.reconflow.entity.UploadedFile;
import com.reconflow.repository.LedgerTransactionRepository;
import com.reconflow.repository.UploadedFileRepository;
import com.reconflow.service.UploadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.StopWatch;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
public class PerformanceTest {

    @Autowired
    private UploadService uploadService;

    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Autowired
    private UploadedFileRepository uploadedFileRepository;

    @Test
    public void testLedgerUploadPerformance_10k_Rows() throws Exception {
        ledgerTransactionRepository.deleteAll();
        uploadedFileRepository.deleteAll();

        // 1. Generate 10,000 rows of CSV data
        int rows = 10000;
        StringBuilder csvContent = new StringBuilder("internal_order_id,gateway_transaction_id,amount,currency,gateway,transaction_date\n");
        for (int i = 0; i < rows; i++) {
            csvContent.append("PERF-ORD-").append(i).append(",")
                    .append("PERF-REF-").append(i).append(",")
                    .append("100.00,INR,RAZORPAY,2026-10-10\n");
        }

        MockMultipartFile file = new MockMultipartFile("file", "perf_ledger.csv", "text/csv", csvContent.toString().getBytes());

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        // 2. Process Upload
        uploadService.processLedgerUpload(file, "RAZORPAY", LocalDate.parse("2026-10-10"));

        stopWatch.stop();
        long timeTakenMillis = stopWatch.getTotalTimeMillis();
        long count = ledgerTransactionRepository.count();

        System.out.println("=========================================");
        System.out.println("PERFORMANCE METRIC: 10,000 Ledger Rows");
        System.out.println("Time Taken: " + timeTakenMillis + " ms");
        System.out.println("Throughput: " + (10000.0 / (timeTakenMillis / 1000.0)) + " rows/sec");
        System.out.println("Rows Inserted: " + count);
        System.out.println("=========================================");

        assertTrue(count == 10000, "Should have inserted exactly 10000 rows");
    }
}
