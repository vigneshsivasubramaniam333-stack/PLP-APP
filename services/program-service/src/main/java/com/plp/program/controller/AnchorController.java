package com.plp.program.controller;

import com.plp.program.audit.AuditHeaders;
import com.plp.program.audit.AuditService;
import com.plp.program.model.entity.Anchor;
import com.plp.program.model.enums.AnchorStatus;
import com.plp.program.repository.AnchorRepository;
import com.plp.program.security.LenderPortalRoleAuthorization;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/anchors")
@RequiredArgsConstructor
public class AnchorController {

    private final AnchorRepository anchorRepository;
    private final AuditService auditService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> createAnchor(
            @RequestBody Anchor anchor,
            @RequestHeader(value = LenderPortalRoleAuthorization.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = AuditHeaders.X_USER_ID, required = false) String userIdHeader,
            @RequestHeader(value = AuditHeaders.X_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = AuditHeaders.X_LINKED_ENTITY_TYPE, required = false) String linkedEntityType) {
        LenderPortalRoleAuthorization.requireCreditAnalystCreate(rolesHeader);
        allocateAnchorCodeIfMissing(anchor);
        Anchor created = anchorRepository.save(anchor);
        auditService.logEvent(
                "ANCHOR_CREATED",
                "ANCHOR",
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
    public ResponseEntity<Map<String, Object>> listAnchors() {
        List<Anchor> anchors = anchorRepository.findAll();
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", anchors));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getAnchor(@PathVariable UUID id) {
        Anchor anchor = anchorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Anchor not found: " + id));
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", anchor));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateAnchor(
            @PathVariable UUID id,
            @RequestBody Anchor updated,
            @RequestHeader(value = LenderPortalRoleAuthorization.HEADER_USER_ROLES, required = false) String rolesHeader) {
        LenderPortalRoleAuthorization.requireCreditAnalystOrManager(rolesHeader);
        Anchor anchor = anchorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Anchor not found: " + id));
        anchor.setEntityName(updated.getEntityName());
        anchor.setContactPersonName(updated.getContactPersonName());
        anchor.setContactEmail(updated.getContactEmail());
        anchor.setContactPhone(updated.getContactPhone());
        anchor.setAddress(updated.getAddress());
        anchor.setBankAccount(updated.getBankAccount());
        anchor.setIntegrationConfig(updated.getIntegrationConfig());
        anchor.setRating(updated.getRating());
        anchorRepository.save(anchor);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", anchor));
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
        Anchor anchor = anchorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Anchor not found: " + id));
        AnchorStatus newStatus = AnchorStatus.valueOf(body.get("status"));
        anchor.setStatus(newStatus);
        anchorRepository.save(anchor);
        if (newStatus == AnchorStatus.ACTIVE) {
            auditService.logEvent(
                    "ANCHOR_APPROVED",
                    "ANCHOR",
                    id.toString(),
                    "APPROVE",
                    userIdHeader,
                    rolesHeader,
                    linkedEntityId,
                    linkedEntityType,
                    "SUCCESS",
                    null);
        }
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", anchor));
    }

    private void allocateAnchorCodeIfMissing(Anchor anchor) {
        if (anchor.getAnchorCode() != null && !anchor.getAnchorCode().isBlank()) {
            anchor.setAnchorCode(anchor.getAnchorCode().trim());
            return;
        }
        for (int i = 0; i < 25; i++) {
            String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
            String candidate = "ANC-" + suffix;
            if (!anchorRepository.existsByAnchorCode(candidate)) {
                anchor.setAnchorCode(candidate);
                return;
            }
        }
        throw new RuntimeException("Unable to generate a unique anchor code");
    }
}
