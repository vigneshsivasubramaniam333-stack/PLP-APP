package com.plp.program.audit;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_events", schema = "plp_program")
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

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
