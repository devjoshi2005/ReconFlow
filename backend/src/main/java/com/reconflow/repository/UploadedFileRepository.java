package com.reconflow.repository;

import com.reconflow.entity.UploadedFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.List;

@Repository
public interface UploadedFileRepository extends JpaRepository<UploadedFile, UUID> {
    boolean existsByFileChecksum(String fileChecksum);
    boolean existsBySourceTypeAndSettlementDate(String sourceType, java.time.LocalDate settlementDate);
    long countByStatusIn(List<String> statuses);

    List<UploadedFile> findAllByOrderByCreatedAtDesc();
}
