package com.reconflow.repository;

import com.reconflow.entity.GatewaySettlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface GatewaySettlementRepository extends JpaRepository<GatewaySettlement, UUID> {
    List<GatewaySettlement> findByGateway(String gateway);
    List<GatewaySettlement> findBySettlementDate(java.time.LocalDate settlementDate);

    @Query("SELECT g FROM GatewaySettlement g "
            + "WHERE g.uploadedFileId = :uploadedFileId "
            + "AND g.gatewayTransactionId IN :gatewayTransactionIds")
    List<GatewaySettlement> findByUploadedFileIdAndGatewayTransactionIdIn(
            @Param("uploadedFileId") UUID uploadedFileId,
            @Param("gatewayTransactionIds") Collection<String> gatewayTransactionIds);
}
