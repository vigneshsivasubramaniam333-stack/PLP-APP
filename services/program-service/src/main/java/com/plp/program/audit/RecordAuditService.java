package com.plp.program.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Writes field-level audit_events rows and full entity snapshots to entity_record_audit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordAuditService {

    private final AuditService auditService;
    private final EntityRecordAuditRepository entityRecordAuditRepository;
    private final ObjectMapper objectMapper;

    public void capture(
            String eventType,
            String entityType,
            String entityId,
            String action,
            String userIdHeader,
            String userRolesHeader,
            String linkedEntityIdHeader,
            String linkedEntityTypeHeader,
            String status,
            String message,
            Object oldEntity,
            Object newEntity) {
        Map<String, Object> oldMap = toMap(oldEntity);
        Map<String, Object> newMap = toMap(newEntity);
        String changed = diffKeys(oldMap, newMap);
        try {
            auditService.logEventWithValues(
                    eventType,
                    entityType,
                    entityId,
                    action,
                    userIdHeader,
                    userRolesHeader,
                    linkedEntityIdHeader,
                    linkedEntityTypeHeader,
                    status,
                    message,
                    oldMap,
                    newMap,
                    changed);
        } catch (Exception e) {
            log.warn("Index audit skipped: {}", e.getMessage());
        }
        try {
            entityRecordAuditRepository.save(EntityRecordAudit.builder()
                    .entityType(truncate(entityType, 100))
                    .entityId(truncate(entityId, 255))
                    .action(truncate(action, 40))
                    .statusAtChange(truncate(status, 64))
                    .performedBy(truncate(userIdHeader, 64))
                    .performedByRole(truncate(userRolesHeader, 500))
                    .oldRow(oldMap)
                    .newRow(newMap)
                    .changedFields(changed)
                    .build());
        } catch (Exception e) {
            log.warn("Entity record audit skipped: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object entity) {
        if (entity == null) {
            return null;
        }
        if (entity instanceof Map<?, ?> m) {
            return new LinkedHashMap<>((Map<String, Object>) m);
        }
        try {
            return objectMapper.convertValue(entity, Map.class);
        } catch (Exception e) {
            return Map.of("toString", String.valueOf(entity));
        }
    }

    private static String diffKeys(Map<String, Object> oldMap, Map<String, Object> newMap) {
        if (oldMap == null && newMap == null) {
            return null;
        }
        if (oldMap == null) {
            return String.join(",", newMap.keySet());
        }
        if (newMap == null) {
            return String.join(",", oldMap.keySet());
        }
        return oldMap.keySet().stream()
                .filter(k -> !Objects.equals(oldMap.get(k), newMap.get(k)))
                .collect(Collectors.joining(","));
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }
}
