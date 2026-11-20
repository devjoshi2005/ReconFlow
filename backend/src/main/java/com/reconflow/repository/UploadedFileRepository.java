package com.reconflow.repository;

import com.reconflow.entity.UploadedFile;
import com.reconflow.entity.status.UploadedFileStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.List;

@Repository
public interface UploadedFileRepository extends JpaRepository<UploadedFile, UUID> {
    boolean existsByFileChecksum(String fileChecksum);
    boolean existsBySourceTypeAndSettlementDate(String sourceType, java.time.LocalDate settlementDate);
    boolean existsBySourceTypeAndSettlementDateAndStatusIn(String sourceType, java.time.LocalDate settlementDate, List<UploadedFileStatus> statuses);
    boolean existsBySourceTypeAndSettlementDateAndStatus(String sourceType, java.time.LocalDate settlementDate, UploadedFileStatus status);
    boolean existsBySourceTypeAndGatewayAndSettlementDateAndStatus(String sourceType, String gateway, java.time.LocalDate settlementDate, UploadedFileStatus status);
    long countByStatusIn(List<UploadedFileStatus> statuses);

    List<UploadedFile> findAllByOrderByCreatedAtDesc();
    List<UploadedFile> findByStatus(UploadedFileStatus status);
    List<UploadedFile> findByStatusAndUpdatedAtBefore(UploadedFileStatus status, java.time.ZonedDateTime cutoff);
}
