package com.plp.program.audit;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuditBridge {

    private final AuditService auditService;

    private static volatile AuditService delegate;

    @PostConstruct
    void register() {
        delegate = auditService;
    }

    public static void accessDenied(
            String entityType,
            String entityId,
            String message,
            String userIdHeader,
            String rolesHeader,
            String linkedEntityIdHeader,
            String linkedEntityTypeHeader) {
        try {
            AuditService svc = delegate;
            if (svc == null) {
                return;
            }
            svc.logEvent(
                    "ACCESS_DENIED",
                    entityType != null ? entityType : "UNKNOWN",
                    entityId != null ? entityId : "",
                    "ACCESS_DENIED",
                    userIdHeader,
                    rolesHeader,
                    linkedEntityIdHeader,
                    linkedEntityTypeHeader,
                    "FAILURE",
                    message != null ? message : "");
        } catch (Exception ignored) {
            // never propagate
        }
    }
}
