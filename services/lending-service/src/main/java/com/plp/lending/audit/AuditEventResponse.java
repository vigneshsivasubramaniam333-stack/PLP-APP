package com.plp.lending.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEventResponse(
        UUID id,
        String eventType,
        String entityType,
        String entityId,
        String action,
        String performedByUserId,
        String performedByRole,
        String linkedEntityId,
        String linkedEntityType,
        String status,
        String message,
        Map<String, Object> oldValues,
        Map<String, Object> newValues,
        String changedFields,
        Instant createdAt) {

    static AuditEventResponse fromEntity(AuditEvent e) {
        return new AuditEventResponse(
                e.getId(),
                e.getEventType(),
                e.getEntityType(),
                e.getEntityId(),
                e.getAction(),
                e.getPerformedByUserId(),
                e.getPerformedByRole(),
                e.getLinkedEntityId(),
                e.getLinkedEntityType(),
                e.getStatus(),
                e.getMessage(),
                e.getOldValues(),
                e.getNewValues(),
                e.getChangedFields(),
                e.getCreatedAt());
    }
}
