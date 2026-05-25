package com.plp.report.controller;

import com.plp.report.model.entity.AuditTrail;
import com.plp.report.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAuditTrail(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Page<AuditTrail> audits = auditService.getAuditTrail(PageRequest.of(page, size));
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "data", audits.getContent(),
                "totalElements", audits.getTotalElements(),
                "totalPages", audits.getTotalPages()
        ));
    }

    @GetMapping("/{entityType}/{entityId}")
    public ResponseEntity<Map<String, Object>> getEntityAuditTrail(
            @PathVariable String entityType, @PathVariable UUID entityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Page<AuditTrail> audits = auditService.getEntityAuditTrail(entityType, entityId, PageRequest.of(page, size));
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "data", audits.getContent(),
                "totalElements", audits.getTotalElements()
        ));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> recordAudit(@RequestBody Map<String, Object> request) {
        AuditTrail audit = auditService.recordAudit(
                (String) request.get("entityType"),
                UUID.fromString(request.get("entityId").toString()),
                (String) request.get("action"),
                request.get("actorId") != null ? UUID.fromString(request.get("actorId").toString()) : null,
                (String) request.get("actorRole"),
                request.get("oldValues") != null ? request.get("oldValues").toString() : null,
                request.get("newValues") != null ? request.get("newValues").toString() : null,
                request.get("metadata") != null ? request.get("metadata").toString() : null
        );
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", audit.getId()));
    }
}
