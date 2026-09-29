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

    @Override
    public void run(String... args) throws Exception {
        if (ledgerTransactionRepository.count() == 0) {
            log.info("Seeding initial mock Ledger Transactions for testing...");
            
            LedgerTransaction tx1 = LedgerTransaction.builder()
                    .internalOrderId("ORDER-001")
                    .gatewayTransactionId("pay_RZP001")
                    .amount(new BigDecimal("1500.00"))
                    .currency("INR")
                    .gateway("RAZORPAY")
                    .status("SUCCESS")
                    .transactionDate(ZonedDateTime.now().minusDays(1))
                    .build();
            
            LedgerTransaction tx2 = LedgerTransaction.builder()
                    .internalOrderId("ORDER-002")
                    .gatewayTransactionId("pay_RZP002")
                    .amount(new BigDecimal("250.50"))
                    .currency("INR")
                    .gateway("RAZORPAY")
                    .status("SUCCESS")
                    .transactionDate(ZonedDateTime.now().minusDays(1))
                    .build();
                    
            ledgerTransactionRepository.saveAll(List.of(tx1, tx2));
            log.info("Seeded 2 mock ledger transactions.");
        }
    }
}
