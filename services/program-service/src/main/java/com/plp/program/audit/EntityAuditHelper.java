package com.plp.program.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class EntityAuditHelper {

    private final RecordAuditService recordAuditService;

    public void captureCreate(
            String entityType,
            String entityId,
            Object snapshot,
            String userId,
            String roles,
            String linkedEntityId,
            String linkedEntityType,
            String message) {
        recordAuditService.capture(
                entityType + "_CREATED",
                entityType,
                entityId,
                "CREATE",
                userId,
                roles,
                linkedEntityId,
                linkedEntityType,
                "SUCCESS",
                message != null ? message : (entityType + " created"),
                null,
                snapshot);
    }

    public void captureUpdate(
            String entityType,
            String entityId,
            Object before,
            Object after,
            String userId,
            String roles,
            String linkedEntityId,
            String linkedEntityType,
            String message) {
        recordAuditService.capture(
                entityType + "_UPDATED",
                entityType,
                entityId,
                "UPDATE",
                userId,
                roles,
                linkedEntityId,
                linkedEntityType,
                "SUCCESS",
                message != null ? message : (entityType + " updated"),
                before,
                after);
    }

    public void captureDelete(
            String entityType,
            String entityId,
            Object before,
            String userId,
            String roles,
            String linkedEntityId,
            String linkedEntityType,
            String message) {
        recordAuditService.capture(
                entityType + "_DELETED",
                entityType,
                entityId,
                "DELETE",
                userId,
                roles,
                linkedEntityId,
                linkedEntityType,
                before instanceof Map<?, ?> m && m.get("status") != null
                        ? String.valueOf(m.get("status"))
                        : "DELETED",
                message != null ? message : (entityType + " deleted"),
                before,
                null);
    }

    public void captureInvoiceStatusChange(
            String invoiceId,
            String invoiceNumber,
            String oldStatus,
            String newStatus,
            String userId,
            String roles,
            String message) {
        Map<String, Object> before = snapshot(invoiceId, invoiceNumber, oldStatus);
        Map<String, Object> after = snapshot(invoiceId, invoiceNumber, newStatus);
        recordAuditService.capture(
                "INVOICE_STATUS_CHANGE",
                "INVOICE",
                invoiceId,
                "STATUS_CHANGE",
                userId,
                roles,
                null,
                null,
                newStatus,
                message != null ? message : ("Invoice status: " + oldStatus + " → " + newStatus),
                before,
                after);
    }

    private static Map<String, Object> snapshot(String invoiceId, String invoiceNumber, String status) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("invoiceId", invoiceId);
        if (invoiceNumber != null) {
            m.put("invoiceNumber", invoiceNumber);
        }
        m.put("status", status);
        return m;
    }
}
