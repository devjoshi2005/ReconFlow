package com.reconflow.entity;
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
