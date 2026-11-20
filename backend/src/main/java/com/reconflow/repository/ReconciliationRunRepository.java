package com.reconflow.repository;

import com.reconflow.entity.ReconciliationRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ReconciliationRunRepository extends JpaRepository<ReconciliationRun, UUID> {
    java.util.Optional<ReconciliationRun> findByRunDate(java.time.LocalDate runDate);

    /**
     * Tolerates any pre-existing duplicate rows produced before the run_date unique
     * constraint existed, while always returning the oldest run for the day.
     */
    java.util.Optional<ReconciliationRun> findFirstByRunDateOrderByStartedAtAsc(java.time.LocalDate runDate);
}
