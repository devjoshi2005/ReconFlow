package com.reconflow.repository;

import com.reconflow.entity.ChainBMatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChainBMatchRepository extends JpaRepository<ChainBMatch, UUID> {
    List<ChainBMatch> findByReconciliationRunIdAndGatewaySettlementIdIn(
            UUID reconciliationRunId, List<UUID> gatewaySettlementIds);
}
