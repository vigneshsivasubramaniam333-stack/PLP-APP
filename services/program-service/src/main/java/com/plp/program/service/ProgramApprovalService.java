package com.plp.program.service;

import com.plp.program.model.entity.Program;
import com.plp.program.model.entity.ProgramApprovalConfig;
import com.plp.program.model.enums.ProgramStatus;
import com.plp.program.repository.ProgramApprovalConfigRepository;
import com.plp.program.security.LenderPortalRoleAuthorization;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramApprovalService {

    private final ProgramService programService;
    private final ProgramApprovalConfigRepository configRepository;

    @Transactional(readOnly = true)
    public ProgramApprovalConfig getConfig() {
        return configRepository.findAll().stream()
                .findFirst()
                .orElseGet(() -> ProgramApprovalConfig.builder()
                        .l1Role("CREDIT_ANALYST")
                        .l2Role("CREDIT_MANAGER")
                        .enabled(true)
                        .build());
    }

    @Transactional
    public ProgramApprovalConfig updateConfig(String l1Role, String l2Role, boolean enabled) {
        ProgramApprovalConfig cfg = getConfig();
        if (cfg.getId() == null) {
            cfg = ProgramApprovalConfig.builder()
                    .l1Role(normalizeRole(l1Role, "CREDIT_ANALYST"))
                    .l2Role(normalizeRole(l2Role, "CREDIT_MANAGER"))
                    .enabled(enabled)
                    .build();
        } else {
            cfg.setL1Role(normalizeRole(l1Role, cfg.getL1Role()));
            cfg.setL2Role(normalizeRole(l2Role, cfg.getL2Role()));
            cfg.setEnabled(enabled);
        }
        return configRepository.save(cfg);
    }

    @Transactional
    public Program submitForL2(UUID programId, String rolesHeader, String userId) {
        ProgramApprovalConfig cfg = getConfig();
        requireRole(rolesHeader, cfg.getL1Role(), "Only L1 approver can submit program for L2 review");
        Program program = programService.getProgram(programId);
        ProgramStatus status = program.getStatus();
        if (status != ProgramStatus.DRAFT) {
            throw new RuntimeException(
                    "Program can be submitted to L2 only from DRAFT (after RM submission or resubmit). Current: "
                            + status);
        }
        program.setStatus(ProgramStatus.PENDING_L2);
        program.setSubmittedAt(Instant.now());
        program.setSubmittedBy(trimUser(userId));
        program.setApprovalRemarks(null);
        program.setSentBackAt(null);
        program.setSentBackBy(null);
        Program saved = programService.saveProgram(program);
        log.info("Program {} submitted for L2 by {}", saved.getProgramCode(), userId);
        return saved;
    }

    @Transactional
    public Program sendBack(UUID programId, String remarks, String rolesHeader, String userId) {
        if (remarks == null || remarks.isBlank()) {
            throw new RuntimeException("Remarks are required when sending back to L1");
        }
        ProgramApprovalConfig cfg = getConfig();
        requireRole(rolesHeader, cfg.getL2Role(), "Only L2 approver can send program back to L1");
        Program program = programService.getProgram(programId);
        if (program.getStatus() != ProgramStatus.PENDING_L2) {
            throw new RuntimeException(
                    "Send back allowed only when status is PENDING_L2. Current: " + program.getStatus());
        }
        // L2 send-back returns the program to L1's working queue for edits and resubmission.
        program.setStatus(ProgramStatus.DRAFT);
        program.setApprovalRemarks(remarks.trim());
        program.setSentBackAt(Instant.now());
        program.setSentBackBy(trimUser(userId));
        program.setSubmittedAt(null);
        program.setSubmittedBy(null);
        Program saved = programService.saveProgram(program);
        log.info("Program {} sent back to L1 by {} with remarks", saved.getProgramCode(), userId);
        return saved;
    }

    /**
     * Send a program back to the relationship manager (RM) for commercial revision.
     * <ul>
     *   <li>L1 from {@link ProgramStatus#DRAFT} → {@link ProgramStatus#SENT_BACK} (remarks optional)</li>
     *   <li>L2 from {@link ProgramStatus#PENDING_L2} → {@link ProgramStatus#SENT_BACK} (remarks required)</li>
     * </ul>
     */
    @Transactional
    public Program sendBackToRm(UUID programId, String remarks, String rolesHeader, String userId) {
        ProgramApprovalConfig cfg = getConfig();
        Program program = programService.getProgram(programId);
        ProgramStatus status = program.getStatus();
        Set<String> roles = LenderPortalRoleAuthorization.parseRoles(rolesHeader);
        boolean platformAdmin = roles.contains("PLATFORM_ADMIN");
        boolean isL1 = platformAdmin || roles.contains(normalizeRole(cfg.getL1Role(), "CREDIT_ANALYST"));
        boolean isL2 = platformAdmin || roles.contains(normalizeRole(cfg.getL2Role(), "CREDIT_MANAGER"));

        if (status == ProgramStatus.DRAFT) {
            if (!isL1) {
                throw new RuntimeException("Only L1 approver can send program back to RM from DRAFT");
            }
            program.setStatus(ProgramStatus.SENT_BACK);
            String trimmed = remarks == null ? null : remarks.trim();
            program.setApprovalRemarks(trimmed == null || trimmed.isEmpty() ? null : trimmed);
            program.setSentBackAt(Instant.now());
            program.setSentBackBy(trimUser(userId));
            Program saved = programService.saveProgram(program);
            log.info("Program {} sent back to RM by L1 {}", saved.getProgramCode(), userId);
            return saved;
        }

        if (status == ProgramStatus.PENDING_L2) {
            if (!isL2) {
                throw new RuntimeException("Only L2 approver can send program back to RM from PENDING_L2");
            }
            if (remarks == null || remarks.isBlank()) {
                throw new RuntimeException("Remarks are required when L2 sends the program back to RM");
            }
            program.setStatus(ProgramStatus.SENT_BACK);
            program.setApprovalRemarks(remarks.trim());
            program.setSentBackAt(Instant.now());
            program.setSentBackBy(trimUser(userId));
            program.setSubmittedAt(null);
            program.setSubmittedBy(null);
            Program saved = programService.saveProgram(program);
            log.info("Program {} sent back to RM by L2 {}", saved.getProgramCode(), userId);
            return saved;
        }

        throw new RuntimeException(
                "Send back to RM allowed only from DRAFT (L1) or PENDING_L2 (L2). Current: " + status);
    }

    @Transactional
    public Program approveL2(UUID programId, String rolesHeader, String userId) {
        ProgramApprovalConfig cfg = getConfig();
        requireRole(rolesHeader, cfg.getL2Role(), "Only L2 approver can approve program");
        Program program = programService.getProgram(programId);
        ProgramStatus status = program.getStatus();
        if (status != ProgramStatus.PENDING_L2 && status != ProgramStatus.DRAFT) {
            throw new RuntimeException(
                    "L2 approval allowed from PENDING_L2 or DRAFT. Current: " + status);
        }
        program.setStatus(ProgramStatus.APPROVED_PENDING_DOCS);
        program.setApprovalRemarks(null);
        Program saved = programService.saveProgram(program);
        log.info("Program {} L2 approved (APPROVED_PENDING_DOCS) by {}", saved.getProgramCode(), userId);
        return saved;
    }

    private static void requireRole(String rolesHeader, String requiredRole, String message) {
        Set<String> roles = LenderPortalRoleAuthorization.parseRoles(rolesHeader);
        String needed = normalizeRole(requiredRole, "CREDIT_MANAGER");
        if (!roles.contains("PLATFORM_ADMIN") && !roles.contains(needed)) {
            throw new RuntimeException(message);
        }
    }

    private static String normalizeRole(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private static String trimUser(String userId) {
        return userId == null ? null : userId.trim();
    }
}
