package com.plp.program.controller;

import com.plp.program.audit.AuditHeaders;
import com.plp.program.audit.AuditService;
import com.plp.program.model.entity.Program;
import com.plp.program.model.entity.ProgramApprovalConfig;
import com.plp.program.security.LenderPortalRoleAuthorization;
import com.plp.program.service.ProgramApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform/program-approval")
@RequiredArgsConstructor
public class ProgramApprovalController {

    private final ProgramApprovalService programApprovalService;
    private final AuditService auditService;

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getApprovalConfig() {
        ProgramApprovalConfig cfg = programApprovalService.getConfig();
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", cfg));
    }

    @PutMapping("/config")
    public ResponseEntity<Map<String, Object>> updateApprovalConfig(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = LenderPortalRoleAuthorization.HEADER_USER_ROLES, required = false)
                    String rolesHeader) {
        LenderPortalRoleAuthorization.requirePlatformAdmin(rolesHeader);
        String l1 = body.get("l1Role") != null ? String.valueOf(body.get("l1Role")) : "CREDIT_ANALYST";
        String l2 = body.get("l2Role") != null ? String.valueOf(body.get("l2Role")) : "CREDIT_MANAGER";
        boolean enabled = body.get("enabled") == null || Boolean.parseBoolean(String.valueOf(body.get("enabled")));
        ProgramApprovalConfig cfg = programApprovalService.updateConfig(l1, l2, enabled);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", cfg));
    }

    @PostMapping("/programs/{id}/submit-l2")
    public ResponseEntity<Map<String, Object>> submitForL2(
            @PathVariable UUID id,
            @RequestHeader(value = LenderPortalRoleAuthorization.HEADER_USER_ROLES, required = false)
                    String rolesHeader,
            @RequestHeader(value = AuditHeaders.X_USER_ID, required = false) String userIdHeader) {
        Program updated = programApprovalService.submitForL2(id, rolesHeader, userIdHeader);
        auditService.logEvent(
                "PROGRAM_SUBMITTED_L2",
                "PROGRAM",
                id.toString(),
                "SUBMIT",
                userIdHeader,
                rolesHeader,
                null,
                null,
                "SUCCESS",
                null);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", updated));
    }

    @PostMapping("/programs/{id}/send-back")
    public ResponseEntity<Map<String, Object>> sendBack(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = LenderPortalRoleAuthorization.HEADER_USER_ROLES, required = false)
                    String rolesHeader,
            @RequestHeader(value = AuditHeaders.X_USER_ID, required = false) String userIdHeader) {
        String remarks = body != null ? body.get("remarks") : null;
        Program updated = programApprovalService.sendBack(id, remarks, rolesHeader, userIdHeader);
        auditService.logEvent(
                "PROGRAM_SENT_BACK",
                "PROGRAM",
                id.toString(),
                "SEND_BACK",
                userIdHeader,
                rolesHeader,
                null,
                null,
                "SUCCESS",
                remarks);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", updated));
    }

    @PostMapping("/programs/{id}/send-back-to-rm")
    public ResponseEntity<Map<String, Object>> sendBackToRm(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> body,
            @RequestHeader(value = LenderPortalRoleAuthorization.HEADER_USER_ROLES, required = false)
                    String rolesHeader,
            @RequestHeader(value = AuditHeaders.X_USER_ID, required = false) String userIdHeader) {
        String remarks = body != null ? body.get("remarks") : null;
        Program updated = programApprovalService.sendBackToRm(id, remarks, rolesHeader, userIdHeader);
        auditService.logEvent(
                "PROGRAM_SENT_BACK_TO_RM",
                "PROGRAM",
                id.toString(),
                "SEND_BACK_TO_RM",
                userIdHeader,
                rolesHeader,
                null,
                null,
                "SUCCESS",
                remarks);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", updated));
    }

    @PostMapping("/programs/{id}/approve-l2")
    public ResponseEntity<Map<String, Object>> approveL2(
            @PathVariable UUID id,
            @RequestHeader(value = LenderPortalRoleAuthorization.HEADER_USER_ROLES, required = false)
                    String rolesHeader,
            @RequestHeader(value = AuditHeaders.X_USER_ID, required = false) String userIdHeader,
            @RequestHeader(value = AuditHeaders.X_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = AuditHeaders.X_LINKED_ENTITY_TYPE, required = false) String linkedEntityType) {
        Program updated = programApprovalService.approveL2(id, rolesHeader, userIdHeader);
        auditService.logEvent(
                "PROGRAM_APPROVED",
                "PROGRAM",
                id.toString(),
                "APPROVE",
                userIdHeader,
                rolesHeader,
                linkedEntityId,
                linkedEntityType,
                "SUCCESS",
                null);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", updated));
    }
}
