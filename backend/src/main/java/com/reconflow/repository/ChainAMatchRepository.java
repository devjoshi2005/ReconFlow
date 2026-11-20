package com.reconflow.repository;

import com.reconflow.entity.ChainAMatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChainAMatchRepository extends JpaRepository<ChainAMatch, UUID> {
    List<ChainAMatch> findByReconciliationRunIdAndLedgerTransactionIdIn(
            UUID reconciliationRunId, List<UUID> ledgerTransactionIds);
}
