package com.plp.program.controller;

import com.plp.program.model.dto.ProgramFieldDefinitionRequest;
import com.plp.program.model.dto.ProgramFieldDefinitionResponse;
import com.plp.program.security.LenderPortalRoleAuthorization;
import com.plp.program.service.ProgramFieldDefinitionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/programs/field-definitions")
@RequiredArgsConstructor
public class ProgramFieldDefinitionController {

    private final ProgramFieldDefinitionService programFieldDefinitionService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(required = false) String productType,
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        List<ProgramFieldDefinitionResponse> data =
                programFieldDefinitionService.list(productType, activeOnly);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", data));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @Valid @RequestBody ProgramFieldDefinitionRequest request,
            @RequestHeader(value = LenderPortalRoleAuthorization.HEADER_USER_ROLES, required = false)
                    String rolesHeader) {
        LenderPortalRoleAuthorization.requireCreditAnalystCreate(rolesHeader);
        ProgramFieldDefinitionResponse created = programFieldDefinitionService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("status", "SUCCESS", "data", created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable UUID id,
            @Valid @RequestBody ProgramFieldDefinitionRequest request,
            @RequestHeader(value = LenderPortalRoleAuthorization.HEADER_USER_ROLES, required = false)
                    String rolesHeader) {
        LenderPortalRoleAuthorization.requireCreditAnalystCreate(rolesHeader);
        ProgramFieldDefinitionResponse updated = programFieldDefinitionService.update(id, request);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(
            @PathVariable UUID id,
            @RequestHeader(value = LenderPortalRoleAuthorization.HEADER_USER_ROLES, required = false)
                    String rolesHeader) {
        LenderPortalRoleAuthorization.requireCreditAnalystCreate(rolesHeader);
        programFieldDefinitionService.delete(id);
        return ResponseEntity.ok(Map.of("status", "SUCCESS"));
    }
}
