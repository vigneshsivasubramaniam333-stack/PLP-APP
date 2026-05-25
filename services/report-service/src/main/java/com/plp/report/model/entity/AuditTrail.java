package com.plp.report.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_trail")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditTrail {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(nullable = false)
    private String action;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_role")
    private String actorRole;

    @Column(name = "old_values", columnDefinition = "jsonb")
    private String oldValues;

    @Column(name = "new_values", columnDefinition = "jsonb")
    private String newValues;

    @Column(columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}
