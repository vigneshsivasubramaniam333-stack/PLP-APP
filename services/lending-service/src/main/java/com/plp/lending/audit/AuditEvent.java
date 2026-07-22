package com.plp.lending.audit;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "audit_events", schema = "plp_lending")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    @Column(name = "entity_id", length = 255)
    private String entityId;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "performed_by_user_id", length = 64)
    private String performedByUserId;

    @Column(name = "performed_by_role", length = 500)
    private String performedByRole;

    @Column(name = "linked_entity_id", length = 64)
    private String linkedEntityId;

    @Column(name = "linked_entity_type", length = 64)
    private String linkedEntityType;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(length = 2000)
    private String message;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_values", columnDefinition = "jsonb")
    private Map<String, Object> oldValues;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_values", columnDefinition = "jsonb")
    private Map<String, Object> newValues;

    @Column(name = "changed_fields", columnDefinition = "text")
    private String changedFields;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
