package com.plp.program.controller;

import com.plp.program.audit.AuditHeaders;
import com.plp.program.audit.AuditService;
import com.plp.program.model.dto.BorrowerCreateRequest;
import com.plp.program.model.entity.Borrower;
import com.plp.program.model.entity.BorrowerLimit;
import com.plp.program.model.enums.BorrowerStatus;
import com.plp.program.repository.BorrowerRepository;
import com.plp.program.security.LenderPortalRoleAuthorization;
import com.plp.program.service.BorrowerProvisioningService;
import com.plp.program.service.LimitService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/borrowers")
@RequiredArgsConstructor
public class BorrowerController {

    private final BorrowerRepository borrowerRepository;
    private final BorrowerProvisioningService borrowerProvisioningService;
    private final LimitService limitService;
    private final AuditService auditService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> createBorrower(
            @RequestBody BorrowerCreateRequest request,
            @RequestHeader(value = LenderPortalRoleAuthorization.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = AuditHeaders.X_USER_ID, required = false) String userIdHeader,
            @RequestHeader(value = AuditHeaders.X_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = AuditHeaders.X_LINKED_ENTITY_TYPE, required = false) String linkedEntityType) {
        LenderPortalRoleAuthorization.requireCreditAnalystCreate(rolesHeader);
        Borrower created = borrowerProvisioningService.createBorrower(request);
        auditService.logEvent(
                "BORROWER_CREATED",
                "BORROWER",
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
    public ResponseEntity<Map<String, Object>> listBorrowers(
            @RequestParam(required = false) UUID programId,
            @RequestParam(required = false) UUID anchorId) {
        List<Borrower> borrowers;
        if (programId != null) {
            borrowers = borrowerRepository.findByProgramId(programId);
        } else if (anchorId != null) {
            borrowers = borrowerRepository.findByAnchorId(anchorId);
        } else {
            borrowers = borrowerRepository.findAll();
        }
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", borrowers));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getBorrower(@PathVariable UUID id) {
        Borrower borrower = borrowerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Borrower not found: " + id));
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", borrower));
    }

    @GetMapping("/{id}/limits")
    public ResponseEntity<Map<String, Object>> getBorrowerLimits(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID programId) {
        if (programId == null) {
            Borrower borrower = borrowerRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Borrower not found: " + id));
            programId = borrower.getProgramId();
        }
        BorrowerLimit limit = limitService.getLimit(id, programId);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", limit));
    }

    @PostMapping("/{id}/limits/block")
    public ResponseEntity<Map<String, Object>> blockLimit(
            @PathVariable UUID id,
            @RequestParam UUID programId,
            @RequestParam BigDecimal amount,
            @RequestHeader(value = LenderPortalRoleAuthorization.HEADER_USER_ROLES, required = false)
                    String rolesHeader) {
        LenderPortalRoleAuthorization.requireCreditAnalystOrManager(rolesHeader);
        BorrowerLimit limit = limitService.blockLimit(id, programId, amount);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", limit));
    }

    @PostMapping("/{id}/limits/release")
    public ResponseEntity<Map<String, Object>> releaseLimit(
            @PathVariable UUID id,
            @RequestParam UUID programId,
            @RequestParam BigDecimal amount,
            @RequestHeader(value = LenderPortalRoleAuthorization.HEADER_USER_ROLES, required = false)
                    String rolesHeader) {
        LenderPortalRoleAuthorization.requireCreditAnalystOrManager(rolesHeader);
        BorrowerLimit limit = limitService.releaseLimit(id, programId, amount);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", limit));
    }

    @PostMapping("/{id}/limits")
    public ResponseEntity<Map<String, Object>> assignLimit(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = LenderPortalRoleAuthorization.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = AuditHeaders.X_USER_ID, required = false) String userIdHeader,
            @RequestHeader(value = AuditHeaders.X_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = AuditHeaders.X_LINKED_ENTITY_TYPE, required = false) String linkedEntityType) {
        LenderPortalRoleAuthorization.requireCreditAnalystOrManager(rolesHeader);
        Borrower borrower = borrowerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Borrower not found: " + id));
        UUID programId = body.containsKey("programId")
                ? UUID.fromString(body.get("programId").toString())
                : borrower.getProgramId();
        BigDecimal sanctionedLimit = new BigDecimal(body.get("sanctionedLimit").toString());
        BigDecimal interestRate = body.containsKey("interestRate")
                ? new BigDecimal(body.get("interestRate").toString())
                : null;
        BorrowerLimit limit = limitService.assignLimit(id, programId, sanctionedLimit, interestRate);
        auditService.logEvent(
                "BORROWER_LINKED",
                "BORROWER",
                id.toString(),
                "LINK",
                userIdHeader,
                rolesHeader,
                linkedEntityId,
                linkedEntityType,
                "SUCCESS",
                "programId=" + programId);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("status", "SUCCESS", "data", limit));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> updateBorrowerStatus(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = LenderPortalRoleAuthorization.HEADER_USER_ROLES, required = false)
                    String rolesHeader) {
        LenderPortalRoleAuthorization.requireCreditManagerApprove(rolesHeader);
        Borrower borrower =
                borrowerRepository.findById(id).orElseThrow(() -> new RuntimeException("Borrower not found: " + id));
        BorrowerStatus newStatus = BorrowerStatus.valueOf(body.get("status"));
        borrower.setStatus(newStatus);
        borrowerRepository.save(borrower);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", borrower));
    }
}
