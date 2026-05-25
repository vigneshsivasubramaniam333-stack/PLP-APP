package com.plp.lending.controller;

import com.plp.lending.audit.AuditBridge;
import com.plp.lending.audit.AuditHeaders;
import com.plp.lending.audit.AuditService;
import com.plp.lending.model.dto.LoanRequestDTO;
import com.plp.lending.model.entity.Loan;
import com.plp.lending.security.LoanAccessGuard;
import com.plp.lending.security.LoanAccessGuard.LoanMutation;
import com.plp.lending.service.LoanService;
import com.plp.lending.service.kfs.KfsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/loans")
@RequiredArgsConstructor
public class LoanController {

    private final LoanService loanService;
    private final KfsService kfsService;
    private final AuditService auditService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> requestLoan(
            @RequestBody LoanRequestDTO dto,
            @RequestHeader(value = LoanAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = LoanAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = LoanAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType,
            @RequestHeader(value = AuditHeaders.X_USER_ID, required = false) String userIdHeader) {
        Set<String> roles = LoanAccessGuard.parseRoles(rolesHeader);
        Loan loan = new Loan();
        loan.setProductType(dto.getProductType());
        loan.setRequestedAmount(dto.getRequestedAmount());
        loan.setInterestRate(dto.getInterestRate());
        loan.setTenureDays(dto.getTenureDays());
        loan.setProcessingFee(dto.getProcessingFee());
        loan.setInvoiceId(dto.getInvoiceId());
        loan.setSalaryDataId(dto.getSalaryDataId());

        if (LoanAccessGuard.isLenderRole(roles)) {
            loan.setBorrowerId(dto.getBorrowerId());
            loan.setProgramId(dto.getProgramId());
            loan.setAnchorId(dto.getAnchorId());
        } else if (LoanAccessGuard.isBorrowerRole(roles)) {
            UUID linkedBorrower =
                    LoanAccessGuard.parseRequiredLinkedBorrower(linkedEntityId, linkedEntityType, rolesHeader);
            loanService.validateBorrowerProgramConsistency(
                    linkedBorrower, dto.getProgramId(), userIdHeader, rolesHeader, linkedEntityId, linkedEntityType);
            loan.setBorrowerId(linkedBorrower);
            loan.setProgramId(dto.getProgramId());
            loan.setAnchorId(null);
        } else {
            AuditBridge.accessDenied(
                    "LOAN",
                    "",
                    LoanAccessGuard.MSG_ACCESS_DENIED,
                    userIdHeader,
                    rolesHeader,
                    linkedEntityId,
                    linkedEntityType);
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.FORBIDDEN, LoanAccessGuard.MSG_ACCESS_DENIED);
        }

        Loan created = loanService.requestLoan(loan);
        auditService.logEvent(
                "LOAN_REQUESTED",
                "LOAN",
                created.getId().toString(),
                "LOAN_REQUESTED",
                userIdHeader,
                rolesHeader,
                linkedEntityId,
                linkedEntityType,
                "SUCCESS",
                null);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("status", "SUCCESS", "data", created));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getLoan(
            @PathVariable UUID id,
            @RequestHeader(value = LoanAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = LoanAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = LoanAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType) {
        Loan loan = loanService.getLoan(id);
        LoanAccessGuard.requireLoanReadAccess(loan, rolesHeader, linkedEntityId, linkedEntityType);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", loan));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listLoans(
            @RequestParam(required = false) UUID borrowerId,
            @RequestParam(required = false) UUID programId,
            @RequestHeader(value = LoanAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = LoanAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = LoanAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType,
            @RequestHeader(value = AuditHeaders.X_USER_ID, required = false) String userIdHeader) {
        Set<String> roles = LoanAccessGuard.parseRoles(rolesHeader);
        List<Loan> loans;
        if (LoanAccessGuard.isLenderRole(roles)) {
            if (borrowerId != null) {
                loans = loanService.getLoansByBorrower(borrowerId);
            } else if (programId != null) {
                loans = loanService.getLoansByProgram(programId);
            } else {
                loans = loanService.getAllLoans();
            }
        } else if (LoanAccessGuard.isBorrowerRole(roles)) {
            UUID scopedBorrower =
                    LoanAccessGuard.parseRequiredLinkedBorrower(linkedEntityId, linkedEntityType, rolesHeader);
            loanService.validateBorrowerProgramConsistency(
                    scopedBorrower, programId, userIdHeader, rolesHeader, linkedEntityId, linkedEntityType);
            loans = loanService.getLoansByBorrower(scopedBorrower);
            if (programId != null) {
                loans = loans.stream()
                        .filter(l -> programId.equals(l.getProgramId()))
                        .collect(Collectors.toList());
            }
        } else if (LoanAccessGuard.isAnchorRole(roles)) {
            UUID anchor = LoanAccessGuard.parseRequiredLinkedAnchor(linkedEntityId, linkedEntityType, rolesHeader);
            loans = loanService.getLoansByAnchor(anchor);
            if (programId != null) {
                loans = loans.stream()
                        .filter(l -> programId.equals(l.getProgramId()))
                        .collect(Collectors.toList());
            }
        } else {
            AuditBridge.accessDenied(
                    "LOAN",
                    "",
                    LoanAccessGuard.MSG_ACCESS_DENIED,
                    userIdHeader,
                    rolesHeader,
                    linkedEntityId,
                    linkedEntityType);
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.FORBIDDEN, LoanAccessGuard.MSG_ACCESS_DENIED);
        }
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", loans));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Map<String, Object>> approveLoan(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = LoanAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = LoanAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = LoanAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType,
            @RequestBody(required = false) Map<String, Object> body) {
        LoanAccessGuard.requireLoanWriteAccess(id, userId, rolesHeader, LoanMutation.SANCTION);
        BigDecimal sanctionedAmount = body != null && body.containsKey("sanctionedAmount")
                ? new BigDecimal(body.get("sanctionedAmount").toString()) : null;
        Loan approved = loanService.approveLoan(id, UUID.fromString(userId), sanctionedAmount);
        auditService.logEvent(
                "LOAN_SANCTIONED",
                "LOAN",
                id.toString(),
                "LOAN_SANCTIONED",
                userId,
                rolesHeader,
                linkedEntityId,
                linkedEntityType,
                "SUCCESS",
                null);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", approved));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Map<String, Object>> rejectLoan(
            @PathVariable UUID id,
            @RequestHeader(value = LoanAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = AuditHeaders.X_USER_ID, required = false) String userId,
            @RequestHeader(value = LoanAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = LoanAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType,
            @RequestBody Map<String, String> body) {
        LoanAccessGuard.requireLoanWriteAccess(id, userId, rolesHeader, LoanMutation.REJECT);
        UUID rejectedBy = userId != null ? UUID.fromString(userId) : null;
        Loan rejected = loanService.rejectLoan(id, body.get("reason"), rejectedBy);
        auditService.logEvent(
                "LOAN_REJECTED",
                "LOAN",
                id.toString(),
                "LOAN_REJECTED",
                userId,
                rolesHeader,
                linkedEntityId,
                linkedEntityType,
                "SUCCESS",
                body.get("reason"));
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", rejected));
    }

    @PostMapping("/{id}/initiate-disbursement")
    public ResponseEntity<Map<String, Object>> initiateDisbursement(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = LoanAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = LoanAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = LoanAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType,
            @RequestBody Map<String, Object> body) {
        LoanAccessGuard.requireLoanWriteAccess(id, userId, rolesHeader, LoanMutation.INITIATE_DISBURSE);
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        Loan loan = loanService.initiateDisbursement(id, amount, UUID.fromString(userId));
        auditService.logEvent(
                "DISBURSEMENT_INITIATED",
                "LOAN",
                id.toString(),
                "DISBURSEMENT_INITIATED",
                userId,
                rolesHeader,
                linkedEntityId,
                linkedEntityType,
                "SUCCESS",
                amount.toPlainString());
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", loan));
    }

    @PostMapping("/{id}/disburse")
    public ResponseEntity<Map<String, Object>> disburseLoan(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = LoanAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = LoanAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = LoanAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType,
            @RequestBody Map<String, Object> body) {
        LoanAccessGuard.requireLoanWriteAccess(id, userId, rolesHeader, LoanMutation.EXECUTE_DISBURSE);
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        Loan disbursed = loanService.markDisbursed(id, amount, UUID.fromString(userId));
        auditService.logEvent(
                "DISBURSEMENT_APPROVED",
                "LOAN",
                id.toString(),
                "DISBURSEMENT_APPROVED",
                userId,
                rolesHeader,
                linkedEntityId,
                linkedEntityType,
                "SUCCESS",
                amount.toPlainString());
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", disbursed));
    }

    @PostMapping("/{id}/cancel-disbursement")
    public ResponseEntity<Map<String, Object>> cancelDisbursement(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = LoanAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = LoanAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = LoanAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType) {
        LoanAccessGuard.requireLoanWriteAccess(id, userId, rolesHeader, LoanMutation.CANCEL_DISBURSEMENT);
        Loan cancelled = loanService.cancelDisbursement(id);
        auditService.logEvent(
                "DISBURSEMENT_CANCELLED",
                "LOAN",
                id.toString(),
                "Cancel disbursement",
                userId,
                rolesHeader,
                linkedEntityId,
                linkedEntityType,
                "SUCCESS",
                cancelled.getRejectionReason());
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", cancelled));
    }

    @PostMapping("/{id}/repay")
    public ResponseEntity<Map<String, Object>> recordRepayment(
            @PathVariable UUID id,
            @RequestHeader(value = LoanAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = LoanAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = LoanAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType,
            @RequestHeader(value = AuditHeaders.X_USER_ID, required = false) String userIdHeader,
            @RequestBody Map<String, Object> body) {
        Loan loan = loanService.getLoan(id);
        LoanAccessGuard.requireRepayAccess(loan, rolesHeader, linkedEntityId, linkedEntityType, userIdHeader);
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        Loan updated = loanService.recordRepayment(id, amount);
        auditService.logEvent(
                "REPAYMENT_RECORDED",
                "LOAN",
                id.toString(),
                "REPAYMENT_RECORDED",
                userIdHeader,
                rolesHeader,
                linkedEntityId,
                linkedEntityType,
                "SUCCESS",
                amount.toPlainString());
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", updated));
    }

    @GetMapping("/{id}/kfs")
    public ResponseEntity<String> getKfs(
            @PathVariable UUID id,
            @RequestHeader(value = LoanAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = LoanAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = LoanAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType) {
        Loan loan = loanService.getLoan(id);
        LoanAccessGuard.requireLoanReadAccess(loan, rolesHeader, linkedEntityId, linkedEntityType);
        String html = kfsService.generateKfs(id);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .header("Content-Disposition", "inline; filename=KFS_" + id + ".html")
                .body(html);
    }

    @GetMapping("/overdue")
    public ResponseEntity<Map<String, Object>> getOverdueLoans(
            @RequestHeader(value = LoanAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = AuditHeaders.X_USER_ID, required = false) String userIdHeader,
            @RequestHeader(value = LoanAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = LoanAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType) {
        Set<String> roles = LoanAccessGuard.parseRoles(rolesHeader);
        if (!LoanAccessGuard.isLenderRole(roles)) {
            AuditBridge.accessDenied(
                    "LOAN",
                    "",
                    LoanAccessGuard.MSG_ACCESS_DENIED,
                    userIdHeader,
                    rolesHeader,
                    linkedEntityId,
                    linkedEntityType);
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.FORBIDDEN, LoanAccessGuard.MSG_ACCESS_DENIED);
        }
        List<Loan> overdue = loanService.getOverdueLoans();
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", overdue));
    }
}
