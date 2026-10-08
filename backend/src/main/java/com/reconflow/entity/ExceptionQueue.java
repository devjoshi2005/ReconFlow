package com.reconflow.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "exception_queue")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExceptionQueue {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "reconciliation_run_id", nullable = false) private UUID reconciliationRunId;
    @Column(name = "match_id", nullable = false) private UUID matchId;
    @Column(name = "match_type", nullable = false) private String matchType;
    @Column(name = "exception_type", nullable = false) private String exceptionType;
    @Column(name = "discrepancy_amount", precision = 15, scale = 2) private BigDecimal discrepancyAmount;
    @Column(nullable = false) private String currency;
    @Column(nullable = false) private String status;
    @Column(name = "gateway") private String gateway;
    @Column(name = "assigned_to") private UUID assignedTo;
    @Column(name = "resolution_notes", columnDefinition = "TEXT") private String resolutionNotes;
    @Column(name = "resolution_type") private String resolutionType;
    @Column(name = "approved_by") private UUID approvedBy;
    @Column(name = "resolved_at") private ZonedDateTime resolvedAt;
    
    // Jira Integration Fields
    @Column(name = "jira_ticket_id", length = 20) private String jiraTicketId;
    @Column(name = "jira_ticket_url", length = 500) private String jiraTicketUrl;
    @Column(name = "jira_priority", length = 20) private String jiraPriority;
    @Column(name = "jira_created_at") private ZonedDateTime jiraCreatedAt;
    @Column(name = "jira_status", length = 30) private String jiraStatus;
    
    @CreationTimestamp @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;
    
    @UpdateTimestamp @Column(name = "updated_at")
    private ZonedDateTime updatedAt;
}
