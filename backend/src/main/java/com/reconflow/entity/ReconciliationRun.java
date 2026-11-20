package com.reconflow.entity;

import com.reconflow.entity.status.ReconciliationRunStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_runs",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_reconciliation_runs_run_date", columnNames = "run_date"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReconciliationRun {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "run_date", nullable = false) private LocalDate runDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false) @Setter(AccessLevel.NONE)
    private ReconciliationRunStatus status;

    @CreationTimestamp @Column(name = "started_at", updatable = false)
    private ZonedDateTime startedAt;
    @Column(name = "completed_at") private ZonedDateTime completedAt;

    @Column(name = "chain_a_total") private Integer chainATotal;
    @Column(name = "chain_a_matched") private Integer chainAMatched;
    @Column(name = "chain_a_exceptions") private Integer chainAExceptions;

    @Column(name = "chain_b_total") private Integer chainBTotal;
    @Column(name = "chain_b_matched") private Integer chainBMatched;
    @Column(name = "chain_b_exceptions") private Integer chainBExceptions;

    @Column(name = "ledger_file_id") private UUID ledgerFileId;
    @Column(name = "gateway_file_id") private UUID gatewayFileId;
    @Column(name = "bank_file_id") private UUID bankFileId;
    @Column(name = "error_message", columnDefinition = "TEXT") private String errorMessage;

    @Version
    private Integer version;

    public void transitionTo(ReconciliationRunStatus newStatus) {
        if (!this.status.canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                    "Invalid state transition: " + this.status + " -> " + newStatus);
        }
        this.status = newStatus;
        if (newStatus == ReconciliationRunStatus.COMPLETED || newStatus == ReconciliationRunStatus.FAILED) {
            this.completedAt = ZonedDateTime.now();
        }
    }
}
