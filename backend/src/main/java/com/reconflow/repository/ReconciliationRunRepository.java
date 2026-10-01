package com.reconflow.repository;

import com.reconflow.entity.ReconciliationRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ReconciliationRunRepository extends JpaRepository<ReconciliationRun, UUID> {
    java.util.Optional<ReconciliationRun> findByRunDate(java.time.LocalDate runDate);
}
