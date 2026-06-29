package com.plp.program.controller;

import com.plp.program.model.dto.EarlyPayCreateRequestDto;
import com.plp.program.model.entity.EarlyPayParameter;
import com.plp.program.model.entity.EarlyPayRequest;
import com.plp.program.security.InvoiceAccessGuard;
import com.plp.program.service.earlypay.EarlyPayService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invoices/early-pay")
@RequiredArgsConstructor
public class EarlyPayBorrowerController {

    private final EarlyPayService earlyPayService;

    @GetMapping("/parameters/today")
    public ResponseEntity<Map<String, Object>> todayParameter(
            @RequestParam UUID subProgramId,
            @RequestParam(required = false) UUID borrowerId,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType) {
        UUID resolvedBorrowerId = resolveBorrowerId(borrowerId, rolesHeader, linkedEntityId, linkedEntityType);
        try {
            EarlyPayParameter param = earlyPayService.getTodayParameterForBorrower(resolvedBorrowerId, subProgramId);
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", param));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @GetMapping("/repayments")
    public ResponseEntity<Map<String, Object>> listRepayments(
            @RequestParam(required = false) UUID borrowerId,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType) {
        UUID resolvedBorrowerId = resolveBorrowerId(borrowerId, rolesHeader, linkedEntityId, linkedEntityType);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "data", earlyPayService.listRepaymentsForBorrower(resolvedBorrowerId)));
    }

    @PostMapping("/requests")
    public ResponseEntity<Map<String, Object>> createRequest(
            @RequestBody EarlyPayCreateRequestDto body,
            @RequestParam(required = false) UUID borrowerId,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType) {
        UUID resolvedBorrowerId = resolveBorrowerId(borrowerId, rolesHeader, linkedEntityId, linkedEntityType);
        try {
            EarlyPayRequest created = earlyPayService.createRequest(resolvedBorrowerId, body);
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", created));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    private static UUID resolveBorrowerId(
            UUID queryBorrowerId,
            String rolesHeader,
            String linkedEntityId,
            String linkedEntityType) {
        Set<String> roles = InvoiceAccessGuard.parseRoles(rolesHeader);
        if (InvoiceAccessGuard.isLenderRole(roles)) {
            if (queryBorrowerId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "borrowerId query parameter required");
            }
            return queryBorrowerId;
        }
        if (!InvoiceAccessGuard.isBorrowerRole(roles)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Borrower or lender role required");
        }
        String t = linkedEntityType == null ? "" : linkedEntityType.trim();
        if (!"BORROWER".equalsIgnoreCase(t) || linkedEntityId == null || linkedEntityId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Borrower linked entity required");
        }
        try {
            UUID borrower = UUID.fromString(linkedEntityId.trim());
            if (queryBorrowerId != null && !queryBorrowerId.equals(borrower)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "borrowerId mismatch");
            }
            return borrower;
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid borrower id");
        }
    }
}
