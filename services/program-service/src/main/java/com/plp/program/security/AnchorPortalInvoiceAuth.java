package com.plp.program.security;

import com.plp.program.audit.AuditBridge;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Anchor portal invoice actions use {@code X-User-Roles} (single role from IAM JWT, e.g. {@code ANCHOR_MAKER}).
 */
public final class AnchorPortalInvoiceAuth {

    private AnchorPortalInvoiceAuth() {
    }

    public static void requireInvoiceUploadRole(String xUserRoles) {
        if (matchesRole(xUserRoles, "ANCHOR_ADMIN") || matchesRole(xUserRoles, "ANCHOR_MAKER")) {
            return;
        }
        String msg = "Invoice upload requires ANCHOR_ADMIN or ANCHOR_MAKER role";
        AuditBridge.accessDenied("ANCHOR_PORTAL_INVOICE", "", msg, null, xUserRoles, null, null);
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, msg);
    }

    public static void requireInvoiceConfirmRole(String xUserRoles) {
        if (matchesRole(xUserRoles, "ANCHOR_ADMIN") || matchesRole(xUserRoles, "ANCHOR_CHECKER")) {
            return;
        }
        String msg = "Invoice confirmation requires ANCHOR_ADMIN or ANCHOR_CHECKER role";
        AuditBridge.accessDenied("ANCHOR_PORTAL_INVOICE", "", msg, null, xUserRoles, null, null);
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, msg);
    }

    /**
     * {@code uploadedByUserId == null} permits confirm (legacy rows).
     * Users with role ANCHOR_ADMIN may confirm own uploads; ANCHOR_CHECKER may not when uploader equals {@code X-User-Id}.
     */
    public static void rejectCheckerConfirmingOwnUpload(String xUserRoles, String xUserId, UUID uploadedByUserId) {
        if (matchesRole(xUserRoles, "ANCHOR_ADMIN")) {
            return;
        }
        if (!matchesRole(xUserRoles, "ANCHOR_CHECKER")) {
            return;
        }
        if (uploadedByUserId == null) {
            return;
        }
        if (xUserId == null || xUserId.isBlank()) {
            return;
        }
        UUID currentUserId = UUID.fromString(xUserId.trim());
        if (uploadedByUserId.equals(currentUserId)) {
            String msg = "Checker cannot confirm an invoice uploaded by the same user";
            AuditBridge.accessDenied("ANCHOR_PORTAL_INVOICE", "", msg, xUserId, xUserRoles, null, null);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, msg);
        }
    }

    private static boolean matchesRole(String header, String roleName) {
        if (header == null || header.isBlank()) {
            return false;
        }
        for (String part : header.split(",")) {
            if (roleName.equals(part.trim())) {
                return true;
            }
        }
        return false;
    }
}
