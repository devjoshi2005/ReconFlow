package com.reconflow.batch;

import com.reconflow.entity.LedgerTransaction;
import com.reconflow.entity.status.LedgerTransactionStatus;
import com.reconflow.repository.LedgerTransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@Slf4j
@Component
public class StaleLedgerSweeperTask {

    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Value("${reconflow.stale-ledger-sweep.days:7}")
    private int staleAfterDays;

    /**
     * Periodically marks LedgerTransactions stuck in PENDING for too long as STALE.
     * Prevents orphaned PENDING records from blocking forever (e.g., gateway CSV never arrived).
     */
    @Scheduled(fixedDelayString = "${reconflow.stale-ledger-sweep.fixed-delay:86400000}")
    @Transactional
    public void sweepStaleLedgerTransactions() {
        ZonedDateTime cutoff = ZonedDateTime.now().minusDays(staleAfterDays);
        long staleCount = ledgerTransactionRepository.sweepStalePending(
                LedgerTransactionStatus.STALE, LedgerTransactionStatus.PENDING, cutoff);
        if (staleCount > 0) {
            log.info("Stale ledger sweep marked {} PENDING transactions as STALE", staleCount);
        }
    }
}
