package com.reconflow.repository;

import com.reconflow.entity.LedgerTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, UUID> {
    Optional<LedgerTransaction> findByGatewayTransactionId(String gatewayTransactionId);

    long countByStatus(String status);
    
    @org.springframework.data.jpa.repository.Query("SELECT COUNT(l) FROM LedgerTransaction l WHERE l.status = 'MATCHED'")
    long countMatched();
}
