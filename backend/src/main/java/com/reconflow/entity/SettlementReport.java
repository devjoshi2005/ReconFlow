package com.reconflow.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "settlement_reports")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SettlementReport {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "reconciliation_run_id") private UUID reconciliationRunId;
    @Column(name = "report_date", nullable = false, unique = true) private LocalDate reportDate;
    @Column(name = "s3_key", nullable = false) private String s3Key;
    @Column(nullable = false) private String status;
    
    @CreationTimestamp @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;
}
