package com.reconflow.repository;

import com.reconflow.entity.LedgerTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.util.Map;

@Repository
public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, UUID> {
    Optional<LedgerTransaction> findByGatewayTransactionId(String gatewayTransactionId);

    org.springframework.data.domain.Page<LedgerTransaction> findByStatusInAndGatewayIgnoreCase(List<String> statuses, String gateway, org.springframework.data.domain.Pageable pageable);

    long countByStatus(String status);
    @org.springframework.data.jpa.repository.Query("SELECT COUNT(l) FROM LedgerTransaction l WHERE l.status = 'RECONCILED'")
    long countMatched();

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(l) FROM LedgerTransaction l WHERE l.createdAt >= :since")
    long countSince(@org.springframework.data.repository.query.Param("since") java.time.ZonedDateTime since);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(l) FROM LedgerTransaction l WHERE l.status = 'RECONCILED' AND l.createdAt >= :since")
    long countMatchedSince(@org.springframework.data.repository.query.Param("since") java.time.ZonedDateTime since);

    @org.springframework.data.jpa.repository.Query("SELECT l.gateway as gateway, COUNT(l) as count FROM LedgerTransaction l WHERE l.status = 'RECONCILED' AND l.createdAt >= :since GROUP BY l.gateway")
    List<Map<String, Object>> countMatchedByGatewaySince(@org.springframework.data.repository.query.Param("since") java.time.ZonedDateTime since);

    @org.springframework.data.jpa.repository.Query("SELECT l.gateway as gateway, COUNT(l) as count FROM LedgerTransaction l WHERE l.createdAt >= :since GROUP BY l.gateway")
    List<Map<String, Object>> countTotalByGatewaySince(@org.springframework.data.repository.query.Param("since") java.time.ZonedDateTime since);

    @org.springframework.data.jpa.repository.Query(value = "SELECT TO_CHAR(created_at, 'Dy') as name, COUNT(id) as processed, SUM(CASE WHEN status = 'RECONCILED' THEN 1 ELSE 0 END) as matched FROM ledger_transactions WHERE created_at >= :since GROUP BY TO_CHAR(created_at, 'Dy'), CAST(created_at AS DATE) ORDER BY CAST(created_at AS DATE) ASC", nativeQuery = true)
    List<Map<String, Object>> getVolumeTimeSeries(@org.springframework.data.repository.query.Param("since") java.time.ZonedDateTime since);
}
