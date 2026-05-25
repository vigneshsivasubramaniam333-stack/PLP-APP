package com.plp.lending.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    /**
     * Persists an audit row. Never throws to callers — failures are swallowed after logging.
     */
    public void logEvent(
            String eventType,
            String entityType,
            String entityId,
            String action,
            String userIdHeader,
            String userRolesHeader,
            String linkedEntityIdHeader,
            String linkedEntityTypeHeader,
            String status,
            String message) {
        try {
            AuditEvent row =
                    AuditEvent.builder()
                            .eventType(truncate(eventType, 100))
                            .entityType(truncate(entityType, 100))
                            .entityId(truncate(entityId, 255))
                            .action(truncate(action, 100))
                            .performedByUserId(truncate(userIdHeader, 64))
                            .performedByRole(truncate(userRolesHeader, 500))
                            .linkedEntityId(truncate(linkedEntityIdHeader, 64))
                            .linkedEntityType(truncate(linkedEntityTypeHeader, 64))
                            .status(truncate(status, 32))
                            .message(truncate(message, 2000))
                            .build();
            auditEventRepository.save(row);
        } catch (Exception e) {
            log.warn("Audit log skipped: {}", e.getMessage());
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }
}
