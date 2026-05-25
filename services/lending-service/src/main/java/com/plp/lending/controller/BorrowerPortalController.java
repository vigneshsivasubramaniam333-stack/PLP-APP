package com.plp.lending.controller;

import com.plp.lending.model.dto.LoanRequestDTO;
import com.plp.lending.model.entity.Loan;
import com.plp.lending.security.BorrowerTenantResolver;
import com.plp.lending.service.EligibilityService;
import com.plp.lending.service.LoanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/portal/borrower")
@RequiredArgsConstructor
public class BorrowerPortalController {

    private final LoanService loanService;
    private final EligibilityService eligibilityService;

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> dashboard(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-Linked-Entity-Type", required = false) String linkedEntityType,
            @RequestHeader(value = "X-Linked-Entity-Id", required = false) String linkedEntityId) {
        BorrowerTenantResolver.requireBorrowerScope(linkedEntityType, linkedEntityId, null, userId);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", Map.of(
                "message", "Borrower portal dashboard",
                "userId", userId
        )));
    }

    @GetMapping("/loans")
    public ResponseEntity<Map<String, Object>> myLoans(
            @RequestParam(required = false) UUID borrowerId,
            @RequestHeader(value = "X-Linked-Entity-Type", required = false) String linkedEntityType,
            @RequestHeader(value = "X-Linked-Entity-Id", required = false) String linkedEntityId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        UUID resolvedBorrowerId = BorrowerTenantResolver.requireBorrowerScope(
                linkedEntityType, linkedEntityId, borrowerId, userId);
        List<Loan> loans = loanService.getLoansByBorrower(resolvedBorrowerId);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", loans));
    }

    @GetMapping("/eligibility")
    public ResponseEntity<Map<String, Object>> checkEligibility(
            @RequestParam(required = false) UUID borrowerId,
            @RequestParam UUID programId,
            @RequestParam BigDecimal requestedAmount,
            @RequestParam(required = false) UUID salaryDataId,
            @RequestHeader(value = "X-Linked-Entity-Type", required = false) String linkedEntityType,
            @RequestHeader(value = "X-Linked-Entity-Id", required = false) String linkedEntityId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        UUID resolvedBorrowerId = BorrowerTenantResolver.requireBorrowerScope(
                linkedEntityType, linkedEntityId, borrowerId, userId);
        Map<String, Object> result = eligibilityService.checkPayDayLoanEligibility(
                resolvedBorrowerId, programId, requestedAmount, salaryDataId);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", result));
    }

    @GetMapping("/invoice-eligibility")
    public ResponseEntity<Map<String, Object>> checkInvoiceEligibility(
            @RequestParam(required = false) UUID borrowerId,
            @RequestParam UUID programId,
            @RequestParam UUID invoiceId,
            @RequestParam BigDecimal requestedAmount,
            @RequestHeader(value = "X-Linked-Entity-Type", required = false) String linkedEntityType,
            @RequestHeader(value = "X-Linked-Entity-Id", required = false) String linkedEntityId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        UUID resolvedBorrowerId = BorrowerTenantResolver.requireBorrowerScope(
                linkedEntityType, linkedEntityId, borrowerId, userId);
        Map<String, Object> result = eligibilityService.checkInvoiceDiscountingEligibility(
                resolvedBorrowerId, programId, invoiceId, requestedAmount);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", result));
    }

    @PostMapping("/loans/request")
    public ResponseEntity<Map<String, Object>> requestLoan(
            @RequestBody LoanRequestDTO dto,
            @RequestHeader(value = "X-Linked-Entity-Type", required = false) String linkedEntityType,
            @RequestHeader(value = "X-Linked-Entity-Id", required = false) String linkedEntityId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        UUID resolvedBorrowerId = BorrowerTenantResolver.requireBorrowerScope(
                linkedEntityType, linkedEntityId, dto.getBorrowerId(), userId);
        Loan loan = new Loan();
        loan.setBorrowerId(resolvedBorrowerId);
        loan.setProgramId(dto.getProgramId());
        loan.setAnchorId(dto.getAnchorId());
        loan.setProductType(dto.getProductType());
        loan.setRequestedAmount(dto.getRequestedAmount());
        loan.setInterestRate(dto.getInterestRate());
        loan.setTenureDays(dto.getTenureDays());
        loan.setProcessingFee(dto.getProcessingFee());
        loan.setInvoiceId(dto.getInvoiceId());
        loan.setSalaryDataId(dto.getSalaryDataId());
        Loan created = loanService.requestLoan(loan);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("status", "SUCCESS", "data", created));
    }
}
