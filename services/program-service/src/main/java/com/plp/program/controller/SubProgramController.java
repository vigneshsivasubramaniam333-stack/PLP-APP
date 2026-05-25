package com.plp.program.controller;

import com.plp.program.audit.AuditBridge;
import com.plp.program.audit.AuditHeaders;
import com.plp.program.audit.AuditService;
import com.plp.program.model.entity.SubProgram;
import com.plp.program.model.entity.SubProgramBorrower;
import com.plp.program.repository.SubProgramBorrowerRepository;
import com.plp.program.security.LenderPortalRoleAuthorization;
import com.plp.program.security.SubProgramAccessGuard;
import com.plp.program.service.SubProgramLimitService;
import com.plp.program.service.SubProgramService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/sub-programs")
@RequiredArgsConstructor
public class SubProgramController {

    private final SubProgramService subProgramService;
    private final SubProgramLimitService subProgramLimitService;
    private final SubProgramBorrowerRepository subProgramBorrowerRepository;
    private final AuditService auditService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestBody SubProgram subProgram,
            @RequestHeader(value = SubProgramAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = AuditHeaders.X_USER_ID, required = false) String userIdHeader,
            @RequestHeader(value = SubProgramAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = SubProgramAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType) {
        SubProgramAccessGuard.requireSubProgramWriteAccess(rolesHeader);
        SubProgram created = subProgramService.createSubProgram(subProgram);
        auditService.logEvent(
                "SUBPROGRAM_CREATED",
                "SUBPROGRAM",
                created.getId().toString(),
                "CREATE",
                userIdHeader,
                rolesHeader,
                linkedEntityId,
                linkedEntityType,
                "SUCCESS",
                null);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("status", "SUCCESS", "data", created));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestHeader(value = SubProgramAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = SubProgramAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = SubProgramAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType) {
        Set<String> roles = SubProgramAccessGuard.parseRoles(rolesHeader);
        List<SubProgram> list;
        if (SubProgramAccessGuard.isLenderRole(roles)) {
            list = subProgramService.listAll();
        } else if (SubProgramAccessGuard.isAnchorRole(roles)) {
            UUID anchor = SubProgramAccessGuard.parseRequiredLinkedAnchor(linkedEntityId, linkedEntityType);
            list = subProgramService.listSubProgramsForAnchor(anchor);
        } else if (SubProgramAccessGuard.isBorrowerRole(roles)) {
            UUID borrower = SubProgramAccessGuard.parseRequiredLinkedBorrower(linkedEntityId, linkedEntityType);
            list = subProgramService.listSubProgramsForBorrower(borrower);
        } else {
            AuditBridge.accessDenied(
                    "SUBPROGRAM",
                    "",
                    SubProgramAccessGuard.MSG_ACCESS_DENIED,
                    null,
                    rolesHeader,
                    linkedEntityId,
                    linkedEntityType);
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.FORBIDDEN, SubProgramAccessGuard.MSG_ACCESS_DENIED);
        }
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", list));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable UUID id,
            @RequestHeader(value = SubProgramAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = SubProgramAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = SubProgramAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType) {
        SubProgram sp = subProgramService.getSubProgram(id);
        SubProgramAccessGuard.requireSubProgramReadAccess(
                sp, rolesHeader, linkedEntityId, linkedEntityType, subProgramBorrowerRepository);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", sp));
    }

    @GetMapping("/{id}/limit-summary")
    public ResponseEntity<Map<String, Object>> subProgramLimitSummary(
            @PathVariable UUID id,
            @RequestHeader(value = SubProgramAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = SubProgramAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = SubProgramAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType) {
        SubProgram sp = subProgramService.getSubProgram(id);
        SubProgramAccessGuard.requireSubProgramReadAccess(
                sp, rolesHeader, linkedEntityId, linkedEntityType, subProgramBorrowerRepository);
        Map<String, Object> summary = subProgramLimitService.getSubProgramLimitSummary(id);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", summary));
    }

    @GetMapping("/{id}/borrowers/{borrowerId}/limit-summary")
    public ResponseEntity<Map<String, Object>> borrowerLimitSummary(
            @PathVariable UUID id,
            @PathVariable UUID borrowerId,
            @RequestHeader(value = SubProgramAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = SubProgramAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = SubProgramAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType) {
        SubProgram sp = subProgramService.getSubProgram(id);
        SubProgramAccessGuard.requireSubProgramReadAccess(
                sp, rolesHeader, linkedEntityId, linkedEntityType, subProgramBorrowerRepository);
        Set<String> roles = SubProgramAccessGuard.parseRoles(rolesHeader);
        if (SubProgramAccessGuard.isBorrowerRole(roles)) {
            UUID linkedBorrower = SubProgramAccessGuard.parseRequiredLinkedBorrower(linkedEntityId, linkedEntityType);
            if (!borrowerId.equals(linkedBorrower)) {
                AuditBridge.accessDenied(
                        "SUBPROGRAM",
                        id.toString(),
                        SubProgramAccessGuard.MSG_ACCESS_DENIED,
                        null,
                        rolesHeader,
                        linkedEntityId,
                        linkedEntityType);
                throw new org.springframework.web.server.ResponseStatusException(
                        HttpStatus.FORBIDDEN, SubProgramAccessGuard.MSG_ACCESS_DENIED);
            }
            SubProgramAccessGuard.requireBorrowerSubProgramAccess(id, linkedBorrower, subProgramBorrowerRepository);
        }
        Map<String, Object> summary = subProgramLimitService.getBorrowerLimitSummary(id, borrowerId);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", summary));
    }

    @PostMapping("/{id}/limits/block")
    public ResponseEntity<Map<String, Object>> blockSubProgramLimits(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = SubProgramAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = SubProgramAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = SubProgramAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType) {
        SubProgramAccessGuard.requireSubProgramWriteAccess(rolesHeader);
        SubProgram sp = subProgramService.getSubProgram(id);
        SubProgramAccessGuard.requireSubProgramReadAccess(
                sp, rolesHeader, linkedEntityId, linkedEntityType, subProgramBorrowerRepository);
        UUID borrowerId = UUID.fromString(body.get("borrowerId").toString());
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        Map<String, Object> data = subProgramLimitService.blockLimits(id, borrowerId, amount);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", data));
    }

    @PostMapping("/{id}/limits/release")
    public ResponseEntity<Map<String, Object>> releaseSubProgramLimits(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = SubProgramAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = SubProgramAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = SubProgramAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType) {
        SubProgramAccessGuard.requireSubProgramWriteAccess(rolesHeader);
        SubProgram sp = subProgramService.getSubProgram(id);
        SubProgramAccessGuard.requireSubProgramReadAccess(
                sp, rolesHeader, linkedEntityId, linkedEntityType, subProgramBorrowerRepository);
        UUID borrowerId = UUID.fromString(body.get("borrowerId").toString());
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        Map<String, Object> data = subProgramLimitService.releaseLimits(id, borrowerId, amount);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", data));
    }

    @PostMapping("/{id}/borrowers")
    public ResponseEntity<Map<String, Object>> addBorrower(
            @PathVariable UUID id,
            @RequestBody SubProgramBorrower membership,
            @RequestHeader(value = SubProgramAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader) {
        SubProgramAccessGuard.requireSubProgramWriteAccess(rolesHeader);
        SubProgramBorrower created = subProgramService.addBorrower(id, membership);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("status", "SUCCESS", "data", created));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Map<String, Object>> approve(
            @PathVariable UUID id,
            @RequestHeader(value = LenderPortalRoleAuthorization.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = AuditHeaders.X_USER_ID, required = false) String userIdHeader,
            @RequestHeader(value = SubProgramAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = SubProgramAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType) {
        LenderPortalRoleAuthorization.requireCreditManagerApprove(rolesHeader);
        try {
            SubProgram sp = subProgramService.approveSubProgram(id);
            auditService.logEvent(
                    "SUBPROGRAM_APPROVED",
                    "SUBPROGRAM",
                    id.toString(),
                    "APPROVE",
                    userIdHeader,
                    rolesHeader,
                    linkedEntityId,
                    linkedEntityType,
                    "SUCCESS",
                    null);
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", sp));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<Map<String, Object>> deactivate(
            @PathVariable UUID id,
            @RequestHeader(value = LenderPortalRoleAuthorization.HEADER_USER_ROLES, required = false) String rolesHeader) {
        LenderPortalRoleAuthorization.requireCreditManagerOperational(rolesHeader);
        try {
            SubProgram sp = subProgramService.deactivateSubProgram(id);
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", sp));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @GetMapping("/{id}/borrowers")
    public ResponseEntity<Map<String, Object>> listBorrowers(
            @PathVariable UUID id,
            @RequestHeader(value = SubProgramAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = SubProgramAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = SubProgramAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType) {
        SubProgram sp = subProgramService.getSubProgram(id);
        SubProgramAccessGuard.requireSubProgramReadAccess(
                sp, rolesHeader, linkedEntityId, linkedEntityType, subProgramBorrowerRepository);
        List<SubProgramBorrower> list = subProgramService.listBorrowers(id);
        Set<String> roles = SubProgramAccessGuard.parseRoles(rolesHeader);
        if (SubProgramAccessGuard.isBorrowerRole(roles)) {
            UUID linkedBorrower = SubProgramAccessGuard.parseRequiredLinkedBorrower(linkedEntityId, linkedEntityType);
            list = list.stream()
                    .filter(row -> row.getBorrowerId().equals(linkedBorrower))
                    .collect(Collectors.toList());
        }
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", list));
    }
}
