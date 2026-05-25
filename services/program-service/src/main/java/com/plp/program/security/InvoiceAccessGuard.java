package com.plp.program.security;

import com.plp.program.audit.AuditBridge;
import com.plp.program.model.entity.Invoice;
import com.plp.program.model.entity.Program;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Tenant isolation for {@code /api/v1/invoices} using gateway-forwarded headers.
 */
public final class InvoiceAccessGuard {

    public static final String HEADER_USER_ROLES = "X-User-Roles";
    public static final String HEADER_LINKED_ENTITY_ID = "X-Linked-Entity-Id";
    public static final String HEADER_LINKED_ENTITY_TYPE = "X-Linked-Entity-Type";

    public static final String MSG_ACCESS_DENIED = "Invoice access denied";
    public static final String MSG_NOT_THIS_ANCHOR = "Invoice does not belong to this anchor";
    public static final String MSG_NOT_THIS_BORROWER = "Invoice does not belong to this borrower";
    public static final String MSG_BORROWER_CANNOT = "Borrower cannot perform this invoice action";

    private static final Set<String> LENDER_ROLES = Set.of(
            "PLATFORM_ADMIN",
            "CREDIT_ANALYST",
            "CREDIT_MANAGER",
            "ACCOUNTS_OFFICER",
            "ACCOUNTS_MANAGER",
            "COMPLIANCE_OFFICER");

    private static final Set<String> ANCHOR_ROLES = Set.of(
            "ANCHOR_ADMIN",
            "ANCHOR_MAKER",
            "ANCHOR_CHECKER");

    private static final String ROLE_BORROWER = "BORROWER";

    private static final String LINK_TYPE_ANCHOR = "ANCHOR";
    private static final String LINK_TYPE_BORROWER = "BORROWER";

    public enum InvoiceWriteOperation {
        VERIFY,
        CONFIRM,
        MARK_DISCOUNTED,
        /** Internal: lending-service after creating an invoice-discounting loan request. */
        MARK_FINANCING_REQUESTED,
        /** Internal: lending-service after cancelling disbursement pending before disbursement completes. */
        CANCEL_FINANCING_REQUESTED
    }

    private InvoiceAccessGuard() {}

    /** Same normalization as lender portal (legacy role aliases). */
    public static Set<String> parseRoles(String rolesHeader) {
        return LenderPortalRoleAuthorization.parseRoles(rolesHeader);
    }

    public static boolean isLenderRole(Set<String> roles) {
        return roles.stream().anyMatch(LENDER_ROLES::contains);
    }

    public static boolean isAnchorRole(Set<String> roles) {
        return roles.stream().anyMatch(ANCHOR_ROLES::contains);
    }

    public static boolean isBorrowerRole(Set<String> roles) {
        return roles.contains(ROLE_BORROWER);
    }

    /**
     * Lenders: any invoice. Anchors: same {@code anchorId}. Borrowers: same {@code borrowerId}.
     */
    public static void requireInvoiceReadAccess(
            Invoice invoice,
            String rolesHeader,
            String linkedEntityIdHeader,
            String linkedEntityTypeHeader) {
        Set<String> roles = parseRoles(rolesHeader);
        if (isLenderRole(roles)) {
            return;
        }
        if (isAnchorRole(roles)) {
            UUID anchor = parseRequiredLinkedUuid(linkedEntityIdHeader, linkedEntityTypeHeader, LINK_TYPE_ANCHOR);
            if (invoice.getAnchorId().equals(anchor)) {
                return;
            }
            throw forbidden(MSG_NOT_THIS_ANCHOR);
        }
        if (isBorrowerRole(roles)) {
            UUID borrower = parseRequiredLinkedUuid(linkedEntityIdHeader, linkedEntityTypeHeader, LINK_TYPE_BORROWER);
            if (invoice.getBorrowerId().equals(borrower)) {
                return;
            }
            throw forbidden(MSG_NOT_THIS_BORROWER);
        }
        throw forbidden(MSG_ACCESS_DENIED);
    }

    /**
     * Borrower-scoped read: lenders OK; borrowers only when invoice belongs to linked borrower; others denied.
     */
    public static void requireInvoiceBorrowerAccess(
            Invoice invoice,
            String rolesHeader,
            String linkedEntityIdHeader,
            String linkedEntityTypeHeader) {
        Set<String> roles = parseRoles(rolesHeader);
        if (isLenderRole(roles)) {
            return;
        }
        if (!isBorrowerRole(roles)) {
            throw forbidden(MSG_ACCESS_DENIED);
        }
        UUID borrower = parseRequiredLinkedUuid(linkedEntityIdHeader, linkedEntityTypeHeader, LINK_TYPE_BORROWER);
        if (invoice.getBorrowerId().equals(borrower)) {
            return;
        }
        throw forbidden(MSG_NOT_THIS_BORROWER);
    }

    /**
     * VERIFY / CONFIRM: lender or owning anchor. MARK_DISCOUNTED: lender only. Borrowers rejected for all.
     */
    public static void requireInvoiceWriteAccess(
            Invoice invoice,
            String rolesHeader,
            String linkedEntityIdHeader,
            String linkedEntityTypeHeader,
            InvoiceWriteOperation operation) {
        Set<String> roles = parseRoles(rolesHeader);
        if (isBorrowerRole(roles)) {
            throw forbidden(MSG_BORROWER_CANNOT);
        }
        if (operation == InvoiceWriteOperation.MARK_DISCOUNTED
                || operation == InvoiceWriteOperation.MARK_FINANCING_REQUESTED
                || operation == InvoiceWriteOperation.CANCEL_FINANCING_REQUESTED) {
            if (isLenderRole(roles)) {
                return;
            }
            throw forbidden(MSG_ACCESS_DENIED);
        }
        // VERIFY, CONFIRM — lender or matching anchor
        if (isLenderRole(roles)) {
            return;
        }
        if (isAnchorRole(roles)) {
            UUID anchor = parseRequiredLinkedUuid(linkedEntityIdHeader, linkedEntityTypeHeader, LINK_TYPE_ANCHOR);
            if (invoice.getAnchorId().equals(anchor)) {
                return;
            }
            throw forbidden(MSG_NOT_THIS_ANCHOR);
        }
        throw forbidden(MSG_ACCESS_DENIED);
    }

    /** POST JSON create: lender any anchor; anchor only own {@code anchorId}; borrower forbidden. */
    public static void requireManualInvoiceCreateAllowed(
            Invoice draft,
            String rolesHeader,
            String linkedEntityIdHeader,
            String linkedEntityTypeHeader) {
        Set<String> roles = parseRoles(rolesHeader);
        if (isBorrowerRole(roles)) {
            throw forbidden(MSG_BORROWER_CANNOT);
        }
        if (isLenderRole(roles)) {
            return;
        }
        if (isAnchorRole(roles)) {
            if (draft.getAnchorId() == null) {
                throw forbidden(MSG_NOT_THIS_ANCHOR);
            }
            UUID anchor = parseRequiredLinkedUuid(linkedEntityIdHeader, linkedEntityTypeHeader, LINK_TYPE_ANCHOR);
            if (draft.getAnchorId().equals(anchor)) {
                return;
            }
            throw forbidden(MSG_NOT_THIS_ANCHOR);
        }
        throw forbidden(MSG_ACCESS_DENIED);
    }

    public static void requireCsvUploadAllowed(
            UUID anchorIdParam,
            String rolesHeader,
            String linkedEntityIdHeader,
            String linkedEntityTypeHeader) {
        Set<String> roles = parseRoles(rolesHeader);
        if (isBorrowerRole(roles)) {
            throw forbidden(MSG_BORROWER_CANNOT);
        }
        if (isLenderRole(roles)) {
            return;
        }
        if (isAnchorRole(roles)) {
            UUID anchor = parseRequiredLinkedUuid(linkedEntityIdHeader, linkedEntityTypeHeader, LINK_TYPE_ANCHOR);
            if (anchorIdParam.equals(anchor)) {
                return;
            }
            throw forbidden(MSG_NOT_THIS_ANCHOR);
        }
        throw forbidden(MSG_ACCESS_DENIED);
    }

    /** GET invoices by program: lender; anchor if program is legacy-anchored match or has a sub-program row for anchor. */
    public static void requireProgramInvoiceListAccess(
            Program program,
            boolean anchorMayAccessProgramScope,
            String rolesHeader,
            String linkedEntityIdHeader,
            String linkedEntityTypeHeader) {
        Set<String> roles = parseRoles(rolesHeader);
        if (isBorrowerRole(roles)) {
            throw forbidden(MSG_BORROWER_CANNOT);
        }
        if (isLenderRole(roles)) {
            return;
        }
        if (isAnchorRole(roles)) {
            parseRequiredLinkedUuid(linkedEntityIdHeader, linkedEntityTypeHeader, LINK_TYPE_ANCHOR);
            if (!anchorMayAccessProgramScope) {
                throw forbidden(MSG_NOT_THIS_ANCHOR);
            }
            return;
        }
        throw forbidden(MSG_ACCESS_DENIED);
    }

    public static void requireAnchorPathMatchesOrLender(
            UUID pathAnchorId,
            String rolesHeader,
            String linkedEntityIdHeader,
            String linkedEntityTypeHeader) {
        Set<String> roles = parseRoles(rolesHeader);
        if (isLenderRole(roles)) {
            return;
        }
        if (isAnchorRole(roles)) {
            UUID anchor = parseRequiredLinkedUuid(linkedEntityIdHeader, linkedEntityTypeHeader, LINK_TYPE_ANCHOR);
            if (pathAnchorId.equals(anchor)) {
                return;
            }
            throw forbidden(MSG_NOT_THIS_ANCHOR);
        }
        if (isBorrowerRole(roles)) {
            throw forbidden(MSG_BORROWER_CANNOT);
        }
        throw forbidden(MSG_ACCESS_DENIED);
    }

    public static void requireBorrowerPathMatchesOrLender(
            UUID pathBorrowerId,
            String rolesHeader,
            String linkedEntityIdHeader,
            String linkedEntityTypeHeader) {
        Set<String> roles = parseRoles(rolesHeader);
        if (isLenderRole(roles)) {
            return;
        }
        if (isBorrowerRole(roles)) {
            UUID borrower = parseRequiredLinkedUuid(linkedEntityIdHeader, linkedEntityTypeHeader, LINK_TYPE_BORROWER);
            if (pathBorrowerId.equals(borrower)) {
                return;
            }
            throw forbidden(MSG_ACCESS_DENIED);
        }
        if (isAnchorRole(roles)) {
            throw forbidden(MSG_ACCESS_DENIED);
        }
        throw forbidden(MSG_ACCESS_DENIED);
    }

    /**
     * Borrower: forced linked borrower id; optional query param must match linked id or absent.
     * Lender: {@code borrowerId} query required and must match invoice borrower.
     */
    public static UUID resolveBorrowerIdForAccept(
            Invoice invoice,
            String rolesHeader,
            String linkedEntityIdHeader,
            String linkedEntityTypeHeader,
            UUID borrowerIdQueryParam) {
        Set<String> roles = parseRoles(rolesHeader);
        if (isLenderRole(roles)) {
            if (borrowerIdQueryParam == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "borrowerId is required");
            }
            if (!invoice.getBorrowerId().equals(borrowerIdQueryParam)) {
                throw forbidden(MSG_ACCESS_DENIED);
            }
            return borrowerIdQueryParam;
        }
        if (isBorrowerRole(roles)) {
            UUID linkedBorrower = parseRequiredLinkedUuid(linkedEntityIdHeader, linkedEntityTypeHeader, LINK_TYPE_BORROWER);
            if (borrowerIdQueryParam != null && !borrowerIdQueryParam.equals(linkedBorrower)) {
                throw forbidden(MSG_ACCESS_DENIED);
            }
            if (!invoice.getBorrowerId().equals(linkedBorrower)) {
                throw forbidden(MSG_NOT_THIS_BORROWER);
            }
            return linkedBorrower;
        }
        throw forbidden(MSG_ACCESS_DENIED);
    }

    private static UUID parseRequiredLinkedUuid(String idStr, String typeStr, String expectedTypeUpper) {
        if (idStr == null || idStr.isBlank()) {
            throw forbidden(MSG_ACCESS_DENIED);
        }
        String t = typeStr == null ? "" : typeStr.trim().toUpperCase(Locale.ROOT);
        if (!expectedTypeUpper.equals(t)) {
            throw forbidden(MSG_ACCESS_DENIED);
        }
        try {
            return UUID.fromString(idStr.trim());
        } catch (IllegalArgumentException ex) {
            throw forbidden(MSG_ACCESS_DENIED);
        }
    }

    private static ResponseStatusException forbidden(String message) {
        AuditBridge.accessDenied("INVOICE", "", message, null, null, null, null);
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }
}
