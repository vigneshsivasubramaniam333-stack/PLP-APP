package com.plp.lending.controller;

import com.plp.lending.model.entity.LmsLoanOperation;
import com.plp.lending.repository.LmsLoanOperationRepository;
import com.plp.lending.security.LenderRoleAuthorization;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/lending-audit")
@RequiredArgsConstructor
public class LendingLmsOpsAuditController {

    private static final int MAX_PAGE_SIZE = 100;

    private final LmsLoanOperationRepository lmsLoanOperationRepository;

    @GetMapping("/lms-operations")
    public ResponseEntity<Map<String, Object>> listLmsOperations(
            @RequestHeader(value = LenderRoleAuthorization.HEADER_USER_ROLES, required = false)
                    String rolesHeader,
            @RequestParam(required = false) UUID loanId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        LenderRoleAuthorization.requireLenderAuditAccess(rolesHeader);

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        var pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<LmsLoanOperation> result = loanId != null
                ? lmsLoanOperationRepository.findByLoanIdOrderByCreatedAtDesc(loanId, pageable)
                : lmsLoanOperationRepository.findAll(pageable);

        Map<String, Object> pageBody = new LinkedHashMap<>();
        pageBody.put("content", result.getContent().stream().map(this::toRow).toList());
        pageBody.put("totalElements", result.getTotalElements());
        pageBody.put("totalPages", result.getTotalPages());
        pageBody.put("number", result.getNumber());
        pageBody.put("size", result.getSize());
        pageBody.put("first", result.isFirst());
        pageBody.put("last", result.isLast());

        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", pageBody));
    }

    private Map<String, Object> toRow(LmsLoanOperation op) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", op.getId());
        row.put("loanId", op.getLoanId());
        row.put("operation", op.getOperation());
        row.put("encoreAccountId", op.getEncoreAccountId());
        row.put("status", op.getStatus());
        row.put("requestJson", op.getRequestJson());
        row.put("responseJson", op.getResponseJson());
        row.put("errorMessage", op.getErrorMessage());
        row.put("createdAt", op.getCreatedAt());
        return row;
    }
}
