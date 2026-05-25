package com.plp.report.service;

import com.plp.report.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditEventConsumer {

    private final AuditService auditService;

    @RabbitListener(queues = RabbitMQConfig.AUDIT_QUEUE)
    public void handleAuditEvent(Map<String, Object> event) {
        try {
            String entityType = (String) event.get("entityType");
            UUID entityId = event.get("entityId") != null ? UUID.fromString(event.get("entityId").toString()) : null;
            String action = (String) event.get("action");
            UUID actorId = event.get("actorId") != null ? UUID.fromString(event.get("actorId").toString()) : null;
            String actorRole = (String) event.get("actorRole");
            String oldValues = event.get("oldValues") != null ? event.get("oldValues").toString() : null;
            String newValues = event.get("newValues") != null ? event.get("newValues").toString() : null;
            String metadata = event.get("metadata") != null ? event.get("metadata").toString() : null;

            if (entityType == null || entityId == null || action == null) {
                log.warn("Skipping audit event — missing required fields");
                return;
            }

            auditService.recordAudit(entityType, entityId, action, actorId, actorRole, oldValues, newValues, metadata);
        } catch (Exception e) {
            log.error("Failed to process audit event: {}", e.getMessage(), e);
        }
    }
}
