package com.reconflow.repository;

import com.reconflow.entity.GatewaySettlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

import java.util.List;

@Repository
public interface GatewaySettlementRepository extends JpaRepository<GatewaySettlement, UUID> {
    List<GatewaySettlement> findByGateway(String gateway);
    List<GatewaySettlement> findBySettlementDate(java.time.LocalDate settlementDate);
}
