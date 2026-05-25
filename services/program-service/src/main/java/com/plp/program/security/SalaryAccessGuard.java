package com.plp.program.security;

import com.plp.program.audit.AuditBridge;
import com.plp.program.model.entity.EmployeeSalaryData;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Tenant isolation for {@code /api/v1/salary} using gateway-forwarded headers.
 */
public final class SalaryAccessGuard {

    public static final String HEADER_USER_ROLES = "X-User-Roles";
    public static final String HEADER_LINKED_ENTITY_ID = "X-Linked-Entity-Id";
    public static final String HEADER_LINKED_ENTITY_TYPE = "X-Linked-Entity-Type";

    public static final String MSG_ACCESS_DENIED = "Salary access denied";
    public static final String MSG_NOT_THIS_ANCHOR = "Salary data does not belong to this anchor";
    public static final String MSG_NOT_THIS_BORROWER = "Salary data does not belong to this borrower";
    public static final String MSG_BORROWER_CANNOT_UPLOAD = "Borrower cannot upload salary data";

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

    /** Resolved query args for {@code GET /salary} after tenant checks. payPeriod null for anchor means all periods. */
    public record ResolvedListQuery(UUID anchorId, UUID borrowerId, String payPeriod) {}

    private SalaryAccessGuard() {}

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

    /** CSV upload: lender any anchorId; anchor anchorId must match linked; borrower forbidden. */
    public static void requireSalaryUploadAccess(
            UUID anchorIdParam,
            String rolesHeader,
            String linkedEntityIdHeader,
            String linkedEntityTypeHeader) {
        Set<String> roles = parseRoles(rolesHeader);
        if (isBorrowerRole(roles)) {
            throw forbidden(MSG_BORROWER_CANNOT_UPLOAD);
        }
        if (isLenderRole(roles)) {
            return;
        }
        if (isAnchorRole(roles)) {
            UUID linkedAnchor = parseRequiredLinkedUuid(linkedEntityIdHeader, linkedEntityTypeHeader, LINK_TYPE_ANCHOR);
            if (!anchorIdParam.equals(linkedAnchor)) {
                throw forbidden(MSG_NOT_THIS_ANCHOR);
            }
            return;
        }
        throw forbidden(MSG_ACCESS_DENIED);
    }

    /** JSON create: lender any body anchor; anchor body's anchorId must match linked; borrower forbidden. */
    public static void requireSalaryCreateAccess(
            EmployeeSalaryData body,
            String rolesHeader,
            String linkedEntityIdHeader,
            String linkedEntityTypeHeader) {
        Set<String> roles = parseRoles(rolesHeader);
        if (isBorrowerRole(roles)) {
            throw forbidden(MSG_BORROWER_CANNOT_UPLOAD);
        }
        if (isLenderRole(roles)) {
            return;
        }
        if (isAnchorRole(roles)) {
            UUID linkedAnchor = parseRequiredLinkedUuid(linkedEntityIdHeader, linkedEntityTypeHeader, LINK_TYPE_ANCHOR);
            if (body.getAnchorId() == null || !body.getAnchorId().equals(linkedAnchor)) {
                throw forbidden(MSG_NOT_THIS_ANCHOR);
            }
            return;
        }
        throw forbidden(MSG_ACCESS_DENIED);
    }

    /**
     * Authorizes {@code GET /salary} filters and returns normalized anchor/borrower for the service layer.
     */
    public static ResolvedListQuery resolveAndAuthorizeListQuery(
            UUID anchorIdQuery,
            UUID borrowerIdQuery,
            String payPeriod,
            String rolesHeader,
            String linkedEntityIdHeader,
            String linkedEntityTypeHeader) {
        Set<String> roles = parseRoles(rolesHeader);
        if (isLenderRole(roles)) {
            return new ResolvedListQuery(anchorIdQuery, borrowerIdQuery, payPeriod);
        }
        if (isBorrowerRole(roles)) {
            UUID linkedBorrower =
                    parseRequiredLinkedUuid(linkedEntityIdHeader, linkedEntityTypeHeader, LINK_TYPE_BORROWER);
            if (anchorIdQuery != null) {
                throw forbidden(MSG_ACCESS_DENIED);
            }
            UUID effectiveBorrower = borrowerIdQuery != null ? borrowerIdQuery : linkedBorrower;
            if (!effectiveBorrower.equals(linkedBorrower)) {
                throw forbidden(MSG_NOT_THIS_BORROWER);
            }
            return new ResolvedListQuery(null, effectiveBorrower, payPeriod);
        }
        if (isAnchorRole(roles)) {
            UUID linkedAnchor =
                    parseRequiredLinkedUuid(linkedEntityIdHeader, linkedEntityTypeHeader, LINK_TYPE_ANCHOR);
            if (borrowerIdQuery != null) {
                throw forbidden(MSG_ACCESS_DENIED);
            }
            UUID effectiveAnchor = anchorIdQuery != null ? anchorIdQuery : linkedAnchor;
            if (!effectiveAnchor.equals(linkedAnchor)) {
                throw forbidden(MSG_NOT_THIS_ANCHOR);
            }
            String normalizedPeriod =
                    payPeriod == null || payPeriod.isBlank() ? null : payPeriod.trim();
            return new ResolvedListQuery(effectiveAnchor, null, normalizedPeriod);
        }
        throw forbidden(MSG_ACCESS_DENIED);
    }

    /** Single row: lender any; borrower own row; anchor row for their anchor. */
    public static void requireSalaryRowReadAccess(
            EmployeeSalaryData row,
            String rolesHeader,
            String linkedEntityIdHeader,
            String linkedEntityTypeHeader) {
        if (row == null) {
            return;
        }
        Set<String> roles = parseRoles(rolesHeader);
        if (isLenderRole(roles)) {
            return;
        }
        if (isBorrowerRole(roles)) {
            UUID linkedBorrower =
                    parseRequiredLinkedUuid(linkedEntityIdHeader, linkedEntityTypeHeader, LINK_TYPE_BORROWER);
            if (!row.getBorrowerId().equals(linkedBorrower)) {
                throw forbidden(MSG_NOT_THIS_BORROWER);
            }
            return;
        }
        if (isAnchorRole(roles)) {
            UUID linkedAnchor =
                    parseRequiredLinkedUuid(linkedEntityIdHeader, linkedEntityTypeHeader, LINK_TYPE_ANCHOR);
            if (!row.getAnchorId().equals(linkedAnchor)) {
                throw forbidden(MSG_NOT_THIS_ANCHOR);
            }
            return;
        }
        throw forbidden(MSG_ACCESS_DENIED);
    }

    /** Status transitions from lending-service — trusted lender (internal) actor only. */
    public static void requireSalarySlipStatusPatchAccess(String rolesHeader) {
        Set<String> roles = parseRoles(rolesHeader);
        if (!isLenderRole(roles)) {
            throw forbidden(MSG_ACCESS_DENIED);
        }
    }

    /**
     * After loading optional latest row: lender OK; borrower path must match linked; anchor row must belong to linked anchor.
     */
    public static void requireLatestSalaryReadAccess(
            EmployeeSalaryData latestRowOrNull,
            UUID pathBorrowerId,
            String rolesHeader,
            String linkedEntityIdHeader,
            String linkedEntityTypeHeader) {
        Set<String> roles = parseRoles(rolesHeader);
        if (isLenderRole(roles)) {
            return;
        }
        if (isBorrowerRole(roles)) {
            UUID linkedBorrower =
                    parseRequiredLinkedUuid(linkedEntityIdHeader, linkedEntityTypeHeader, LINK_TYPE_BORROWER);
            if (!pathBorrowerId.equals(linkedBorrower)) {
                throw forbidden(MSG_NOT_THIS_BORROWER);
            }
            return;
        }
        if (isAnchorRole(roles)) {
            UUID linkedAnchor =
                    parseRequiredLinkedUuid(linkedEntityIdHeader, linkedEntityTypeHeader, LINK_TYPE_ANCHOR);
            if (latestRowOrNull == null) {
                return;
            }
            if (!latestRowOrNull.getAnchorId().equals(linkedAnchor)) {
                throw forbidden(MSG_NOT_THIS_ANCHOR);
            }
            return;
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
        AuditBridge.accessDenied("SALARY", "", message, null, null, null, null);
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }
}
