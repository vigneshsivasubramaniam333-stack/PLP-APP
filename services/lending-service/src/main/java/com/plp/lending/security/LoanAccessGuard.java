package com.plp.lending.security;

import com.plp.lending.audit.AuditBridge;
import com.plp.lending.model.entity.Loan;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Tenant isolation for {@code /api/v1/loans} using gateway-forwarded headers.
 */
public final class LoanAccessGuard {

    public static final String HEADER_USER_ROLES = "X-User-Roles";
    public static final String HEADER_LINKED_ENTITY_ID = "X-Linked-Entity-Id";
    public static final String HEADER_LINKED_ENTITY_TYPE = "X-Linked-Entity-Type";

    public static final String MSG_ACCESS_DENIED = "Loan access denied";
    public static final String MSG_NOT_THIS_BORROWER = "Loan does not belong to this borrower";
    public static final String MSG_NOT_THIS_ANCHOR = "Loan does not belong to this anchor";

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

    public enum LoanMutation {
        SANCTION,
        REJECT,
        INITIATE_DISBURSE,
        EXECUTE_DISBURSE,
        /** Release a sanctioned loan from disbursement workflow (ACCOUNTS_MANAGER / PLATFORM_ADMIN). */
        CANCEL_DISBURSEMENT
    }

    private LoanAccessGuard() {}

    public static Set<String> parseRoles(String rolesHeader) {
        return LenderRoleAuthorization.parseRoles(rolesHeader);
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

    /** Lender any loan; borrower same borrowerId; anchor same anchorId. */
    public static void requireLoanReadAccess(
            Loan loan,
            String rolesHeader,
            String linkedEntityIdHeader,
            String linkedEntityTypeHeader) {
        Set<String> roles = parseRoles(rolesHeader);
        if (isLenderRole(roles)) {
            return;
        }
        if (isBorrowerRole(roles)) {
            UUID borrower =
                    parseRequiredLinkedUuid(
                            linkedEntityIdHeader, linkedEntityTypeHeader, LINK_TYPE_BORROWER, rolesHeader);
            if (loan.getBorrowerId().equals(borrower)) {
                return;
            }
            throw denied(loan.getId(), MSG_NOT_THIS_BORROWER, rolesHeader, linkedEntityIdHeader, linkedEntityTypeHeader, null);
        }
        if (isAnchorRole(roles)) {
            UUID anchor =
                    parseRequiredLinkedUuid(
                            linkedEntityIdHeader, linkedEntityTypeHeader, LINK_TYPE_ANCHOR, rolesHeader);
            if (loan.getAnchorId().equals(anchor)) {
                return;
            }
            throw denied(loan.getId(), MSG_NOT_THIS_ANCHOR, rolesHeader, linkedEntityIdHeader, linkedEntityTypeHeader, null);
        }
        throw denied(loan.getId(), MSG_ACCESS_DENIED, rolesHeader, linkedEntityIdHeader, linkedEntityTypeHeader, null);
    }

    /** Borrower must own the loan row. */
    public static void requireBorrowerLoanAccess(
            Loan loan,
            UUID linkedBorrowerId,
            String rolesHeader,
            String linkedEntityIdHeader,
            String linkedEntityTypeHeader) {
        if (!loan.getBorrowerId().equals(linkedBorrowerId)) {
            throw denied(loan.getId(), MSG_NOT_THIS_BORROWER, rolesHeader, linkedEntityIdHeader, linkedEntityTypeHeader, null);
        }
    }

    /**
     * Lender workflow mutations with correct lender sub-role (sanction = credit analyst admin;
     * disburse steps = treasury roles).
     */
    public static void requireLoanWriteAccess(
            UUID loanId, String userIdHeader, String rolesHeader, LoanMutation mutation) {
        try {
            switch (mutation) {
                case SANCTION -> LenderRoleAuthorization.requireSanctionRoles(rolesHeader);
                case REJECT -> LenderRoleAuthorization.requireRejectRoles(rolesHeader);
                case INITIATE_DISBURSE -> LenderRoleAuthorization.requireInitiateDisburseRoles(rolesHeader);
                case EXECUTE_DISBURSE -> LenderRoleAuthorization.requireExecuteDisburseRoles(rolesHeader);
                case CANCEL_DISBURSEMENT -> LenderRoleAuthorization.requireCancelDisburseRoles(rolesHeader);
            }
        } catch (RuntimeException e) {
            AuditBridge.accessDenied(
                    "LOAN",
                    loanId != null ? loanId.toString() : "",
                    e.getMessage(),
                    userIdHeader,
                    rolesHeader,
                    null,
                    null);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        }
    }

    /** Repay: accounts officer (or platform admin), or borrower on own loan only. */
    public static void requireRepayAccess(
            Loan loan,
            String rolesHeader,
            String linkedEntityIdHeader,
            String linkedEntityTypeHeader,
            String userIdHeader) {
        Set<String> roles = parseRoles(rolesHeader);
        if (roles.contains("PLATFORM_ADMIN") || roles.contains("ACCOUNTS_OFFICER")) {
            return;
        }
        if (isBorrowerRole(roles)) {
            UUID borrower =
                    parseRequiredLinkedUuid(
                            linkedEntityIdHeader, linkedEntityTypeHeader, LINK_TYPE_BORROWER, rolesHeader);
            requireBorrowerLoanAccess(loan, borrower, rolesHeader, linkedEntityIdHeader, linkedEntityTypeHeader);
            return;
        }
        throw denied(loan.getId(), MSG_ACCESS_DENIED, rolesHeader, linkedEntityIdHeader, linkedEntityTypeHeader, userIdHeader);
    }

    public static UUID parseRequiredLinkedBorrower(
            String linkedEntityIdHeader, String linkedEntityTypeHeader, String rolesHeader) {
        return parseRequiredLinkedUuid(linkedEntityIdHeader, linkedEntityTypeHeader, LINK_TYPE_BORROWER, rolesHeader);
    }

    public static UUID parseRequiredLinkedAnchor(
            String linkedEntityIdHeader, String linkedEntityTypeHeader, String rolesHeader) {
        return parseRequiredLinkedUuid(linkedEntityIdHeader, linkedEntityTypeHeader, LINK_TYPE_ANCHOR, rolesHeader);
    }

    private static UUID parseRequiredLinkedUuid(
            String idStr,
            String typeStr,
            String expectedTypeUpper,
            String rolesHeader) {
        if (idStr == null || idStr.isBlank()) {
            AuditBridge.accessDenied(
                    "LOAN",
                    "",
                    MSG_ACCESS_DENIED,
                    null,
                    rolesHeader,
                    idStr,
                    typeStr);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, MSG_ACCESS_DENIED);
        }
        String t = typeStr == null ? "" : typeStr.trim().toUpperCase(Locale.ROOT);
        if (!expectedTypeUpper.equals(t)) {
            AuditBridge.accessDenied(
                    "LOAN",
                    "",
                    MSG_ACCESS_DENIED,
                    null,
                    rolesHeader,
                    idStr,
                    typeStr);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, MSG_ACCESS_DENIED);
        }
        try {
            return UUID.fromString(idStr.trim());
        } catch (IllegalArgumentException ex) {
            AuditBridge.accessDenied(
                    "LOAN",
                    "",
                    MSG_ACCESS_DENIED,
                    null,
                    rolesHeader,
                    idStr,
                    typeStr);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, MSG_ACCESS_DENIED);
        }
    }

    private static ResponseStatusException denied(
            UUID loanId,
            String message,
            String rolesHeader,
            String linkedEntityIdHeader,
            String linkedEntityTypeHeader,
            String userIdHeader) {
        AuditBridge.accessDenied(
                "LOAN",
                loanId != null ? loanId.toString() : "",
                message,
                userIdHeader,
                rolesHeader,
                linkedEntityIdHeader,
                linkedEntityTypeHeader);
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }
}
