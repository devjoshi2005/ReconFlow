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

import com.reconflow.entity.User;
import com.reconflow.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

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
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        log.info("Clearing old mock data for a clean slate...");
        exceptionQueueRepository.deleteAll();
        chainBMatchRepository.deleteAll();
        chainAMatchRepository.deleteAll();
        gatewaySettlementRepository.deleteAll();
        uploadedFileRepository.deleteAll();
        ledgerTransactionRepository.deleteAll();
        userRepository.deleteAll();

        log.info("Seeding Mock Users...");
        seedUser("analyst", "analyst", "ROLE_ANALYST", "Analyst User");
        seedUser("admin", "admin", "ROLE_ADMIN", "Admin User");
        seedUser("manager", "manager", "ROLE_MANAGER", "Manager User");

        log.info("Ledger data seeding removed as it will be provided via file upload.");
    }

    private void seedUser(String username, String rawPassword, String role, String name) {
        if (userRepository.findByUsername(username).isEmpty()) {
            userRepository.save(User.builder()
                    .username(username)
                    .password(passwordEncoder.encode(rawPassword))
                    .role(role)
                    .name(name)
                    .build());
        }
    }
}
