package com.reconflow.entity;
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
