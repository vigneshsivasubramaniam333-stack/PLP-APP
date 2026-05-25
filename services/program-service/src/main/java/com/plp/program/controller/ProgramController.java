package com.plp.program.controller;

import com.plp.program.audit.AuditHeaders;
import com.plp.program.audit.AuditService;
import com.plp.program.model.dto.ProgramEditDto;
import com.plp.program.model.entity.Program;
import com.plp.program.model.entity.SubProgram;
import com.plp.program.model.enums.ProgramStatus;
import com.plp.program.repository.SubProgramBorrowerRepository;
import com.plp.program.security.LenderPortalRoleAuthorization;
import com.plp.program.security.SubProgramAccessGuard;
import com.plp.program.service.ProgramService;
import com.plp.program.service.SubProgramService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/programs")
@RequiredArgsConstructor
public class ProgramController {

    private final ProgramService programService;
    private final SubProgramService subProgramService;
    private final SubProgramBorrowerRepository subProgramBorrowerRepository;
    private final AuditService auditService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> createProgram(
            @RequestBody Program program,
            @RequestHeader(value = LenderPortalRoleAuthorization.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = AuditHeaders.X_USER_ID, required = false) String userIdHeader,
            @RequestHeader(value = AuditHeaders.X_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = AuditHeaders.X_LINKED_ENTITY_TYPE, required = false) String linkedEntityType) {
        LenderPortalRoleAuthorization.requireCreditAnalystCreate(rolesHeader);
        Program created = programService.createProgram(program);
        auditService.logEvent(
                "PROGRAM_CREATED",
                "PROGRAM",
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
    public ResponseEntity<Map<String, Object>> listPrograms() {
        List<Program> programs = programService.listPrograms();
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", programs));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getProgram(@PathVariable UUID id) {
        Program program = programService.getProgramEnriched(id);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", program));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateProgram(
            @PathVariable UUID id,
            @RequestBody ProgramEditDto body,
            @RequestHeader(value = LenderPortalRoleAuthorization.HEADER_USER_ROLES, required = false) String rolesHeader) {
        LenderPortalRoleAuthorization.requireCreditAnalystOrManager(rolesHeader);
        Program updated = programService.updateProgram(id, body);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", updated));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> updateStatus(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = LenderPortalRoleAuthorization.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = AuditHeaders.X_USER_ID, required = false) String userIdHeader,
            @RequestHeader(value = AuditHeaders.X_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = AuditHeaders.X_LINKED_ENTITY_TYPE, required = false) String linkedEntityType) {
        LenderPortalRoleAuthorization.requireCreditManagerApprove(rolesHeader);
        ProgramStatus newStatus = ProgramStatus.valueOf(body.get("status"));
        Program updated = programService.updateStatus(id, newStatus);
        if (newStatus == ProgramStatus.ACTIVE) {
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
        }
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", updated));
    }

    @GetMapping("/{programId}/sub-programs")
    public ResponseEntity<Map<String, Object>> listSubPrograms(
            @PathVariable UUID programId,
            @RequestHeader(value = SubProgramAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = SubProgramAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = SubProgramAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType) {
        Program program = programService.getProgram(programId);
        List<SubProgram> list = subProgramService.listByProgramId(programId);
        List<SubProgram> filtered = SubProgramAccessGuard.filterSubProgramsUnderProgram(
                list, program, rolesHeader, linkedEntityId, linkedEntityType, subProgramBorrowerRepository);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", filtered));
    }

    @GetMapping("/{id}/utilization")
    public ResponseEntity<Map<String, Object>> getUtilization(@PathVariable UUID id) {
        Map<String, Object> utilization = programService.getUtilization(id);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", utilization));
    }
}
