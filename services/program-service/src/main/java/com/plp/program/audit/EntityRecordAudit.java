package com.plp.program.audit;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "entity_record_audit", schema = "plp_program")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EntityRecordAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    @Column(name = "entity_id", nullable = false, length = 255)
    private String entityId;

    @Column(nullable = false, length = 40)
    private String action;

    @Column(name = "status_at_change", length = 64)
    private String statusAtChange;

    @Column(name = "performed_by", length = 64)
    private String performedBy;

    @Column(name = "performed_by_role", length = 500)
    private String performedByRole;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_row", columnDefinition = "jsonb")
    private Map<String, Object> oldRow;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_row", columnDefinition = "jsonb")
    private Map<String, Object> newRow;

    @Column(name = "changed_fields", columnDefinition = "text")
    private String changedFields;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
