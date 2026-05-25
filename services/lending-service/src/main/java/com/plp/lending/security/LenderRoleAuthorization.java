package com.plp.lending.security;

import com.plp.lending.audit.AuditBridge;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Parses {@code X-User-Roles} (comma-separated role names) and enforces lender-side rules.
 */
public final class LenderRoleAuthorization {

    public static final String HEADER_USER_ROLES = "X-User-Roles";

    public static final String MSG_SANCTION = "Only Credit Analyst or Platform Admin can sanction loans";
    public static final String MSG_REJECT = "Only Credit Analyst or Platform Admin can reject loans";
    public static final String MSG_INITIATE_DISBURSE = "Only Accounts Officer or Platform Admin can initiate disbursement";
    public static final String MSG_APPROVE_DISBURSE = "Only Accounts Manager or Platform Admin can approve disbursement";
    public static final String MSG_CANCEL_DISBURSE =
            "Only Accounts Manager or Platform Admin can cancel a pending disbursement";
    public static final String MSG_REPAY = "Only Accounts Officer or Platform Admin can record repayment";
    public static final String MSG_PENDING_DISBURSE = "Loan must be pending disbursement before disbursement approval";

    private static final Set<String> SANCTION_ROLES = Set.of("PLATFORM_ADMIN", "CREDIT_ANALYST");
    private static final Set<String> INITIATE_DISBURSE_ROLES = Set.of("PLATFORM_ADMIN", "ACCOUNTS_OFFICER");
    private static final Set<String> EXECUTE_DISBURSE_ROLES = Set.of("PLATFORM_ADMIN", "ACCOUNTS_MANAGER");
    private static final Set<String> REPAY_ROLES = Set.of("PLATFORM_ADMIN", "ACCOUNTS_OFFICER");

    private static final Set<String> AUDIT_VIEWER_ROLES =
            Set.of(
                    "PLATFORM_ADMIN",
                    "CREDIT_ANALYST",
                    "CREDIT_MANAGER",
                    "ACCOUNTS_OFFICER",
                    "ACCOUNTS_MANAGER",
                    "COMPLIANCE_OFFICER");

    private LenderRoleAuthorization() {}

    public static Set<String> parseRoles(String header) {
        if (header == null || header.isBlank()) {
            return Set.of();
        }
        Set<String> roles = Arrays.stream(header.split(","))
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(HashSet::new));
        // Temporary: JWT / headers may still carry legacy role names until tokens expire.
        if (roles.contains("PROGRAM_MANAGER")) {
            roles.add("CREDIT_MANAGER");
        }
        if (roles.contains("TREASURY")) {
            roles.add("ACCOUNTS_OFFICER");
        }
        return roles;
    }

    public static void requireSanctionRoles(String rolesHeader) {
        requireAnyRole(rolesHeader, SANCTION_ROLES, MSG_SANCTION);
    }

    public static void requireRejectRoles(String rolesHeader) {
        requireAnyRole(rolesHeader, SANCTION_ROLES, MSG_REJECT);
    }

    public static void requireInitiateDisburseRoles(String rolesHeader) {
        requireAnyRole(rolesHeader, INITIATE_DISBURSE_ROLES, MSG_INITIATE_DISBURSE);
    }

    public static void requireExecuteDisburseRoles(String rolesHeader) {
        requireAnyRole(rolesHeader, EXECUTE_DISBURSE_ROLES, MSG_APPROVE_DISBURSE);
    }

    /** Same cohort as disburse approval: treasury release of a pending disbursement. */
    public static void requireCancelDisburseRoles(String rolesHeader) {
        requireAnyRole(rolesHeader, EXECUTE_DISBURSE_ROLES, MSG_CANCEL_DISBURSE);
    }

    public static void requireRepayRoles(String rolesHeader) {
        requireAnyRole(rolesHeader, REPAY_ROLES, MSG_REPAY);
    }

    /** Read audit trail (lending-service events). */
    public static void requireLenderAuditAccess(String rolesHeader) {
        Set<String> roles = parseRoles(rolesHeader);
        if (roles.contains("PLATFORM_ADMIN")) {
            return;
        }
        boolean ok = roles.stream().anyMatch(AUDIT_VIEWER_ROLES::contains);
        if (!ok) {
            String msg = "Audit trail is restricted to lender operations roles";
            AuditBridge.accessDenied("LENDER_PORTAL_RBAC", "", msg, null, rolesHeader, null, null);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, msg);
        }
    }

    private static void requireAnyRole(String header, Set<String> allowedRoles, String errorMessage) {
        Set<String> roles = parseRoles(header);
        boolean ok = roles.stream().anyMatch(allowedRoles::contains);
        if (!ok) {
            throw new RuntimeException(errorMessage);
        }
    }
}
