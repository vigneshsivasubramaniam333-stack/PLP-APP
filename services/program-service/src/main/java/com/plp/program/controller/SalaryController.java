package com.plp.program.controller;

import com.plp.program.model.entity.EmployeeSalaryData;
import com.plp.program.model.enums.SalarySlipStatus;
import com.plp.program.security.SalaryAccessGuard;
import com.plp.program.security.SalaryAccessGuard.ResolvedListQuery;
import com.plp.program.service.SalaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/salary")
@RequiredArgsConstructor
public class SalaryController {

    private final SalaryService salaryService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadSalaryCsv(
            @RequestParam UUID anchorId,
            @RequestParam UUID programId,
            @RequestParam String payPeriod,
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = SalaryAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = SalaryAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = SalaryAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        SalaryAccessGuard.requireSalaryUploadAccess(anchorId, rolesHeader, linkedEntityId, linkedEntityType);
        try {
            UUID uploadedBy = userId != null ? UUID.fromString(userId) : null;
            List<EmployeeSalaryData> results = salaryService.uploadSalaryCsv(
                    anchorId, programId, payPeriod, file.getInputStream(), uploadedBy);
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "data", Map.of("rowsProcessed", results.size(), "records", results)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createSalaryEntry(
            @RequestBody EmployeeSalaryData salaryData,
            @RequestHeader(value = SalaryAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = SalaryAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = SalaryAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType) {
        SalaryAccessGuard.requireSalaryCreateAccess(salaryData, rolesHeader, linkedEntityId, linkedEntityType);
        EmployeeSalaryData created = salaryService.createOrUpdateSalary(salaryData);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("status", "SUCCESS", "data", created));
    }

    @GetMapping("/{salaryId}")
    public ResponseEntity<Map<String, Object>> getSalaryById(
            @PathVariable UUID salaryId,
            @RequestHeader(value = SalaryAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = SalaryAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = SalaryAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType) {
        EmployeeSalaryData row =
                salaryService.findById(salaryId).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Salary slip not found"));
        SalaryAccessGuard.requireSalaryRowReadAccess(row, rolesHeader, linkedEntityId, linkedEntityType);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", row));
    }

    @PostMapping("/{salaryId}/slip-status")
    public ResponseEntity<Map<String, Object>> patchSlipStatus(
            @PathVariable UUID salaryId,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = SalaryAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader) {
        SalaryAccessGuard.requireSalarySlipStatusPatchAccess(rolesHeader);
        String raw = body == null ? null : body.get("status");
        if (raw == null || raw.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", "status is required"));
        }
        final SalarySlipStatus next;
        try {
            next = SalarySlipStatus.valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("status", "ERROR", "message", "Invalid salary slip status: " + raw));
        }
        EmployeeSalaryData updated = salaryService.updateSlipStatus(salaryId, next);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", updated));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listSalaryData(
            @RequestParam(required = false) UUID anchorId,
            @RequestParam(required = false) UUID borrowerId,
            @RequestParam(required = false) String payPeriod,
            @RequestHeader(value = SalaryAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = SalaryAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = SalaryAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType) {
        ResolvedListQuery q = SalaryAccessGuard.resolveAndAuthorizeListQuery(
                anchorId, borrowerId, payPeriod, rolesHeader, linkedEntityId, linkedEntityType);
        List<EmployeeSalaryData> data;
        if (q.borrowerId() != null) {
            data = salaryService.getByBorrower(q.borrowerId());
        } else if (q.anchorId() != null) {
            if (q.payPeriod() != null) {
                data = salaryService.getByAnchorAndPeriod(q.anchorId(), q.payPeriod());
            } else {
                data = salaryService.getAllByAnchorIdOrdered(q.anchorId());
            }
        } else {
            data = List.of();
        }
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", data));
    }

    @GetMapping("/borrower/{borrowerId}/latest")
    public ResponseEntity<Map<String, Object>> getLatestSalary(
            @PathVariable UUID borrowerId,
            @RequestHeader(value = SalaryAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = SalaryAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = SalaryAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType) {
        var latest = salaryService.getLatestByBorrower(borrowerId);
        SalaryAccessGuard.requireLatestSalaryReadAccess(
                latest.orElse(null), borrowerId, rolesHeader, linkedEntityId, linkedEntityType);
        return latest
                .map(d -> ResponseEntity.ok(Map.<String, Object>of("status", "SUCCESS", "data", d)))
                .orElse(ResponseEntity.ok(Map.of("status", "SUCCESS", "data", Map.of())));
    }
}
