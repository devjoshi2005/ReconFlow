package com.reconflow.repository;

import com.reconflow.entity.LedgerTransaction;
import com.reconflow.entity.status.LedgerTransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.util.Map;

@Repository
public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, UUID> {
    List<LedgerTransaction> findByInternalOrderIdIn(List<String> internalOrderIds);

    Optional<LedgerTransaction> findByGatewayTransactionId(String gatewayTransactionId);

    org.springframework.data.domain.Page<LedgerTransaction> findByStatusInAndGatewayIgnoreCase(List<LedgerTransactionStatus> statuses, String gateway, org.springframework.data.domain.Pageable pageable);

    long countByStatus(LedgerTransactionStatus status);
    @Query("SELECT COUNT(l) FROM LedgerTransaction l WHERE l.status = 'RECONCILED'")
    long countMatched();

    @Query("SELECT COUNT(l) FROM LedgerTransaction l WHERE l.createdAt >= :since")
    long countSince(@Param("since") java.time.ZonedDateTime since);

    @Query("SELECT COUNT(l) FROM LedgerTransaction l WHERE l.status = 'RECONCILED' AND l.createdAt >= :since")
    long countMatchedSince(@Param("since") java.time.ZonedDateTime since);

    @Query("SELECT l.gateway as gateway, COUNT(l) as count FROM LedgerTransaction l WHERE l.status = 'RECONCILED' AND l.createdAt >= :since GROUP BY l.gateway")
    List<Map<String, Object>> countMatchedByGatewaySince(@Param("since") java.time.ZonedDateTime since);

    @Query("SELECT l.gateway as gateway, COUNT(l) as count FROM LedgerTransaction l WHERE l.createdAt >= :since GROUP BY l.gateway")
    List<Map<String, Object>> countTotalByGatewaySince(@Param("since") java.time.ZonedDateTime since);

    @Query(value = "SELECT TO_CHAR(created_at, 'Dy') as name, COUNT(id) as processed, SUM(CASE WHEN status = 'RECONCILED' THEN 1 ELSE 0 END) as matched FROM ledger_transactions WHERE created_at >= :since GROUP BY TO_CHAR(created_at, 'Dy'), CAST(created_at AS DATE) ORDER BY CAST(created_at AS DATE) ASC", nativeQuery = true)
    List<Map<String, Object>> getVolumeTimeSeries(@Param("since") java.time.ZonedDateTime since);

    @Modifying
    @Query("UPDATE LedgerTransaction l SET l.status = :stale WHERE l.status = :pending AND l.transactionDate < :cutoff")
    int sweepStalePending(@Param("stale") LedgerTransactionStatus stale,
                          @Param("pending") LedgerTransactionStatus pending,
                          @Param("cutoff") java.time.ZonedDateTime cutoff);
}
