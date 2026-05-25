package com.plp.program.security;

import com.plp.program.audit.AuditBridge;
import com.plp.program.model.entity.Program;
import com.plp.program.model.entity.SubProgram;
import com.plp.program.repository.SubProgramBorrowerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Tenant isolation for sub-program APIs ({@code /api/v1/sub-programs} and nested program routes).
 */
public final class SubProgramAccessGuard {

    public static final String HEADER_USER_ROLES = "X-User-Roles";
    public static final String HEADER_LINKED_ENTITY_ID = "X-Linked-Entity-Id";
    public static final String HEADER_LINKED_ENTITY_TYPE = "X-Linked-Entity-Type";

    public static final String MSG_ACCESS_DENIED = "Sub-program access denied";
    public static final String MSG_NOT_THIS_ANCHOR = "Sub-program does not belong to this anchor";
    public static final String MSG_BORROWER_NOT_LINKED = "Borrower not linked to this sub-program";

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

    private SubProgramAccessGuard() {}

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

    /** Create / add borrower / limit mutations: lender roles only. */
    public static void requireSubProgramWriteAccess(String rolesHeader) {
        Set<String> roles = parseRoles(rolesHeader);
        if (isLenderRole(roles)) {
            return;
        }
        throw forbidden(MSG_ACCESS_DENIED);
    }

    /**
     * Read single sub-program: lender any; anchor same {@code anchorId}; borrower must appear in
     * {@code sub_program_borrowers}.
     */
    public static void requireSubProgramReadAccess(
            SubProgram subProgram,
            String rolesHeader,
            String linkedEntityIdHeader,
            String linkedEntityTypeHeader,
            SubProgramBorrowerRepository borrowerRepository) {
        Set<String> roles = parseRoles(rolesHeader);
        if (isLenderRole(roles)) {
            return;
        }
        if (isAnchorRole(roles)) {
            UUID anchor = parseRequiredLinkedUuid(linkedEntityIdHeader, linkedEntityTypeHeader, LINK_TYPE_ANCHOR);
            if (subProgram.getAnchorId().equals(anchor)) {
                return;
            }
            throw forbidden(MSG_NOT_THIS_ANCHOR);
        }
        if (isBorrowerRole(roles)) {
            UUID borrower = parseRequiredLinkedUuid(linkedEntityIdHeader, linkedEntityTypeHeader, LINK_TYPE_BORROWER);
            requireBorrowerSubProgramAccess(subProgram.getId(), borrower, borrowerRepository);
            return;
        }
        throw forbidden(MSG_ACCESS_DENIED);
    }

    /** Borrower must have a row in {@code sub_program_borrowers} for this sub-program. */
    public static void requireBorrowerSubProgramAccess(
            UUID subProgramId,
            UUID linkedBorrowerId,
            SubProgramBorrowerRepository borrowerRepository) {
        if (borrowerRepository.findBySubProgramIdAndBorrowerId(subProgramId, linkedBorrowerId).isEmpty()) {
            throw forbidden(MSG_BORROWER_NOT_LINKED);
        }
    }

    /**
     * {@code GET /programs/{programId}/sub-programs}: lender sees all; anchor sees own anchor rows after program check;
     * borrower sees linked memberships only.
     */
    public static List<SubProgram> filterSubProgramsUnderProgram(
            List<SubProgram> subPrograms,
            Program program,
            String rolesHeader,
            String linkedEntityIdHeader,
            String linkedEntityTypeHeader,
            SubProgramBorrowerRepository borrowerRepository) {
        Set<String> roles = parseRoles(rolesHeader);
        if (isLenderRole(roles)) {
            return subPrograms;
        }
        if (isAnchorRole(roles)) {
            UUID anchor = parseRequiredLinkedUuid(linkedEntityIdHeader, linkedEntityTypeHeader, LINK_TYPE_ANCHOR);
            if (program.getAnchorId() != null) {
                if (!program.getAnchorId().equals(anchor)) {
                    throw forbidden(MSG_NOT_THIS_ANCHOR);
                }
            }
            return subPrograms.stream()
                    .filter(sp -> sp.getAnchorId().equals(anchor))
                    .collect(Collectors.toList());
        }
        if (isBorrowerRole(roles)) {
            UUID borrower = parseRequiredLinkedUuid(linkedEntityIdHeader, linkedEntityTypeHeader, LINK_TYPE_BORROWER);
            return subPrograms.stream()
                    .filter(sp -> borrowerRepository.findBySubProgramIdAndBorrowerId(sp.getId(), borrower).isPresent())
                    .collect(Collectors.toList());
        }
        throw forbidden(MSG_ACCESS_DENIED);
    }

    public static UUID parseRequiredLinkedAnchor(String linkedEntityIdHeader, String linkedEntityTypeHeader) {
        return parseRequiredLinkedUuid(linkedEntityIdHeader, linkedEntityTypeHeader, LINK_TYPE_ANCHOR);
    }

    public static UUID parseRequiredLinkedBorrower(String linkedEntityIdHeader, String linkedEntityTypeHeader) {
        return parseRequiredLinkedUuid(linkedEntityIdHeader, linkedEntityTypeHeader, LINK_TYPE_BORROWER);
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
        AuditBridge.accessDenied("SUBPROGRAM", "", message, null, null, null, null);
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }
}
