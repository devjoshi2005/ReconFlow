import os

entities = {
    "User": """package com.reconflow.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, unique = true) private String email;
    private String name;
    @Column(nullable = false) private String role;
    @CreationTimestamp @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;
}
""",
    "GatewaySettlement": """package com.reconflow.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "gateway_settlements")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GatewaySettlement {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "uploaded_file_id") private UUID uploadedFileId;
    @Column(nullable = false) private String gateway;
    @Column(name = "gateway_transaction_id", nullable = false) private String gatewayTransactionId;
    @Column(name = "order_id") private String orderId;
    @Column(name = "settlement_batch_id") private String settlementBatchId;
    @Column(name = "gross_amount", nullable = false, precision = 15, scale = 2) private BigDecimal grossAmount;
    @Column(name = "fee_amount", precision = 19, scale = 4) private BigDecimal feeAmount;
    @Column(name = "tax_amount", precision = 19, scale = 4) private BigDecimal taxAmount;
    @Column(name = "net_amount", nullable = false, precision = 15, scale = 2) private BigDecimal netAmount;
    @Column(name = "bank_transfer_ref") private String bankTransferRef;
    @Column(name = "settlement_date", nullable = false) private LocalDate settlementDate;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_data")
    private String rawData;
    
    @CreationTimestamp @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;
}
""",
    "BankStatement": """package com.reconflow.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "bank_statements")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BankStatement {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "uploaded_file_id") private UUID uploadedFileId;
    @Column(name = "bank_name", nullable = false) private String bankName;
    @Column(name = "bank_account_no", nullable = false) private String bankAccountNo;
    @Column(name = "transaction_date", nullable = false) private LocalDate transactionDate;
    @Column(nullable = false, precision = 15, scale = 2) private BigDecimal amount;
    @Column(nullable = false) private String direction;
    @Column(name = "utr_number") private String utrNumber;
    private String narration;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_data")
    private String rawData;
    
    @CreationTimestamp @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;
}
""",
    "ReconciliationRun": """package com.reconflow.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_runs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReconciliationRun {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "run_date", nullable = false, unique = true) private LocalDate runDate;
    @Column(nullable = false) private String status;
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
}
""",
    "ChainAMatch": """package com.reconflow.entity;
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
""",
    "ChainBMatch": """package com.reconflow.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "chain_b_matches")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ChainBMatch {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "reconciliation_run_id", nullable = false) private UUID reconciliationRunId;
    @Column(name = "gateway_settlement_id") private UUID gatewaySettlementId;
    @Column(name = "bank_statement_id") private UUID bankStatementId;
    @Column(name = "match_status", nullable = false) private String matchStatus;
    @Column(name = "discrepancy_amount", precision = 15, scale = 2) private BigDecimal discrepancyAmount;
    
    @CreationTimestamp @Column(name = "matched_at", updatable = false)
    private ZonedDateTime matchedAt;
}
""",
    "ExceptionQueue": """package com.reconflow.entity;
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
    @Column(name = "assigned_to") private UUID assignedTo;
    @Column(name = "resolution_notes", columnDefinition = "TEXT") private String resolutionNotes;
    @Column(name = "resolution_type") private String resolutionType;
    @Column(name = "approved_by") private UUID approvedBy;
    @Column(name = "resolved_at") private ZonedDateTime resolvedAt;
    
    @CreationTimestamp @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;
    
    @UpdateTimestamp @Column(name = "updated_at")
    private ZonedDateTime updatedAt;
}
""",
    "AuditLog": """package com.reconflow.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.ZonedDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_logs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "entity_name", nullable = false) private String entityName;
    @Column(name = "entity_id", nullable = false) private UUID entityId;
    @Column(nullable = false) private String action;
    @Column(name = "performed_by") private UUID performedBy;
    
    @JdbcTypeCode(SqlTypes.JSON)
    private String changes;
    
    @Column(name = "ip_address") private String ipAddress;
    
    @CreationTimestamp @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;
}
""",
    "SettlementReport": """package com.reconflow.entity;
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
"""
}

base_path = "src/main/java/com/reconflow/entity"
os.makedirs(base_path, exist_ok=True)

for name, content in entities.items():
    with open(f"{base_path}/{name}.java", "w") as f:
        f.write(content)

print("Entities generated successfully.")
