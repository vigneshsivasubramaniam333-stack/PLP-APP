package com.plp.program.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Lender portal RBAC for program-service (headers set by API gateway from JWT).
 */
public final class LenderPortalRoleAuthorization {

    public static final String HEADER_USER_ROLES = "X-User-Roles";

    private static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";

    /** Roles allowed to view centralized audit APIs (lender portal). */
    private static final Set<String> AUDIT_VIEWER_ROLES =
            Set.of(
                    "PLATFORM_ADMIN",
                    "CREDIT_ANALYST",
                    "CREDIT_MANAGER",
                    "ACCOUNTS_OFFICER",
                    "ACCOUNTS_MANAGER",
                    "COMPLIANCE_OFFICER");

    private LenderPortalRoleAuthorization() {}

    public static Set<String> parseRoles(String header) {
        if (header == null || header.isBlank()) {
            return Set.of();
        }
        Set<String> roles = Arrays.stream(header.split(","))
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(HashSet::new));
        if (roles.contains("PROGRAM_MANAGER")) {
            roles.add("CREDIT_MANAGER");
        }
        if (roles.contains("TREASURY")) {
            roles.add("ACCOUNTS_OFFICER");
        }
        return roles;
    }

    private static boolean hasPlatformAdmin(Set<String> roles) {
        return roles.contains(PLATFORM_ADMIN);
    }

    private static void requireAnyRole(String rolesHeader, Set<String> allowed, String message) {
        Set<String> roles = parseRoles(rolesHeader);
        if (hasPlatformAdmin(roles)) {
            return;
        }
        boolean ok = roles.stream().anyMatch(allowed::contains);
        if (!ok) {
            com.plp.program.audit.AuditBridge.accessDenied(
                    "LENDER_PORTAL_RBAC",
                    "",
                    message,
                    null,
                    rolesHeader,
                    null,
                    null);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
        }
    }

    /** Create program / sub-program / borrower / anchor */
    public static void requireCreditAnalystCreate(String rolesHeader) {
        requireAnyRole(rolesHeader, Set.of("CREDIT_ANALYST"),
                "Only Credit Analyst or Platform Admin can create this resource");
    }

    /** Approve program status, sub-program, anchor status */
    public static void requireCreditManagerApprove(String rolesHeader) {
        requireAnyRole(rolesHeader, Set.of("CREDIT_MANAGER"),
                "Only Credit Manager or Platform Admin can approve this action");
    }

    /** Update program configuration (not status-only approve). */
    public static void requireCreditAnalystOrManager(String rolesHeader) {
        requireAnyRole(rolesHeader, Set.of("CREDIT_ANALYST", "CREDIT_MANAGER"),
                "Only Credit Analyst, Credit Manager, or Platform Admin can update this resource");
    }

    /** Sub-program deactivate / operational changes by manager. */
    public static void requireCreditManagerOperational(String rolesHeader) {
        requireCreditManagerApprove(rolesHeader);
    }

    /** Read audit trail (program-service events). */
    public static void requireLenderAuditAccess(String rolesHeader) {
        requireAnyRole(
                rolesHeader,
                AUDIT_VIEWER_ROLES,
                "Audit trail is restricted to lender operations roles");
    }
}
