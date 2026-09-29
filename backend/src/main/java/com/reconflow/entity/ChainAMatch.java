package com.reconflow.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "chain_a_matches")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ChainAMatch {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "reconciliation_run_id", nullable = false) private UUID reconciliationRunId;
    @Column(name = "ledger_transaction_id") private UUID ledgerTransactionId;
    @Column(name = "gateway_settlement_id") private UUID gatewaySettlementId;
    @Column(name = "match_status", nullable = false) private String matchStatus;
    @Column(name = "discrepancy_amount", precision = 15, scale = 2) private BigDecimal discrepancyAmount;
    
    @CreationTimestamp @Column(name = "matched_at", updatable = false)
    private ZonedDateTime matchedAt;
}
