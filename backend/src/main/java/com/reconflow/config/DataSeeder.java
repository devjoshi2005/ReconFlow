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

        log.info("Ledger data seeding removed as it will be provided via file upload.");
    }
}
