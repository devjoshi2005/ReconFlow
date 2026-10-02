package com.reconflow.config;

import com.reconflow.entity.LedgerTransaction;
import com.reconflow.repository.LedgerTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final com.reconflow.repository.ExceptionQueueRepository exceptionQueueRepository;
    private final com.reconflow.repository.UploadedFileRepository uploadedFileRepository;
    private final com.reconflow.repository.GatewaySettlementRepository gatewaySettlementRepository;
    private final com.reconflow.repository.ChainAMatchRepository chainAMatchRepository;
    private final com.reconflow.repository.ChainBMatchRepository chainBMatchRepository;

    @Override
    public void run(String... args) throws Exception {
        log.info("Clearing old mock data for a clean slate...");
        exceptionQueueRepository.deleteAll();
        chainBMatchRepository.deleteAll();
        chainAMatchRepository.deleteAll();
        gatewaySettlementRepository.deleteAll();
        uploadedFileRepository.deleteAll();
        ledgerTransactionRepository.deleteAll();

        log.info("Seeding initial real Ledger Transactions for testing...");
        
        LedgerTransaction tx1 = LedgerTransaction.builder()
                .internalOrderId("order_qkqfeNdSqiY3Uv") // From CSV line 2
                .gatewayTransactionId("pay_jEveHwusAVE3qv")
                .amount(new BigDecimal("1983.00")) // 198300 paise
                .currency("INR")
                .gateway("RAZORPAY")
                .status("INITIATED")
                .transactionDate(ZonedDateTime.now())
                .build();
        
        LedgerTransaction tx2 = LedgerTransaction.builder()
                .internalOrderId("order_hxEykCpZj6R5aD") // From CSV line 3
                .gatewayTransactionId("pay_a8mRVtLLCWPgEu")
                .amount(new BigDecimal("7999.00")) // 799900 paise
                .currency("INR")
                .gateway("RAZORPAY")
                .status("INITIATED")
                .transactionDate(ZonedDateTime.now())
                .build();
                
        ledgerTransactionRepository.saveAll(List.of(tx1, tx2));
        log.info("Seeded 2 real ledger transactions that will perfectly match the razorpay_recon.csv.");
    }
}
