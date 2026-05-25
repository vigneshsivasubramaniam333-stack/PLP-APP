package com.plp.lending.service;

import com.plp.lending.exception.LendingBusinessException;
import com.plp.lending.exception.RestClientIntegrationMapper;
import com.plp.lending.event.LoanEventPublisher;
import com.plp.lending.audit.AuditBridge;
import com.plp.lending.audit.AuditService;
import com.plp.lending.integration.ProgramServiceAuthHeaders;
import com.plp.lending.integration.ProgramServiceInvoiceSubProgramValidator;
import com.plp.lending.integration.ProgramServiceProgramConfigClient;
import com.plp.lending.integration.ProgramServiceSalarySlipClient;
import com.plp.lending.integration.ProgramServiceSubProgramLimits;
import com.plp.lending.lms.PlpLmsOrchestrator;
import com.plp.lending.security.LenderRoleAuthorization;
import com.plp.lending.security.LoanAccessGuard;
import com.plp.lending.model.entity.Loan;
import com.plp.lending.model.enums.LoanStatus;
import com.plp.lending.repository.LoanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoanService {

    private static final String FLOW_PURCHASE_BILL_DISCOUNTING = "PURCHASE_BILL_DISCOUNTING";
    private static final String FLOW_SALES_BILL_DISCOUNTING = "SALES_BILL_DISCOUNTING";
    private static final String FLOW_PAY_LOAN = "PAY_LOAN";
    private static final String FLOW_PAY_DAY_LOAN = "PAY_DAY_LOAN";

    private static final String MSG_ID_INVOICE_DISCOUNTING_INTEREST =
            "Interest rate is not configured for the selected sub-program";
    private static final String INVOICE_STATUS_FINANCING_REQUESTED = "FINANCING_REQUESTED";
    private static final String MSG_PAY_DAY_LOAN_LIMIT_INSUFFICIENT =
            "Available limit is insufficient for this loan amount";

    private static final String MSG_LOAN_TENURE_NOT_CONFIGURED =
            "Loan tenure is not configured for this sub-program";

    /** Block a second discounting request while an invoice-linked loan is still in a non-terminal lifecycle. */
    private static final String MSG_INVOICE_DISCOUNTING_ALREADY_REQUESTED =
            "Discounting request already exists for this invoice.";

    /** Pay Day loan exists for the same salary row — blocks a duplicate request while the prior loan is in a tying status. */
    private static final String MSG_PAY_DAY_LOAN_EXISTS_FOR_SALARY_PERIOD =
            "Loan already exists for this salary period.";

    /**
     * Pay Day / Pay Loan lifecycle stages that tie {@code salaryDataId} (duplicate request not allowed). Excludes
     * REJECTED/CANCELLED so a slip can be reused after rejection; includes CLOSED so a slip is not reused after closure.
     */
    private static final List<LoanStatus> PAY_DAY_SALARY_ACTIVE_STATUSES = List.of(
            LoanStatus.REQUESTED,
            LoanStatus.ELIGIBILITY_CHECK,
            LoanStatus.SANCTIONED,
            LoanStatus.DISBURSEMENT_PENDING,
            LoanStatus.DISBURSED,
            LoanStatus.REPAYMENT_DUE,
            LoanStatus.OVERDUE,
            LoanStatus.CLOSED);

    /**
     * Open invoice-discounting loan rows for the same invoice (excludes terminal outcomes so a new request is allowed
     * after close / reject / cancel / write-off).
     */
    private static final List<LoanStatus> INVOICE_DISCOUNTING_OPEN_LOAN_STATUSES = List.of(
            LoanStatus.REQUESTED,
            LoanStatus.ELIGIBILITY_CHECK,
            LoanStatus.SANCTIONED,
            LoanStatus.DISBURSEMENT_PENDING,
            LoanStatus.DISBURSED,
            LoanStatus.REPAYMENT_DUE,
            LoanStatus.OVERDUE);

    private static final String SLIP_LOAN_REQUESTED = "LOAN_REQUESTED";
    private static final String SLIP_REJECTED_AGAIN = "REJECTED_AVAILABLE_AGAIN";
    private static final String SLIP_DISBURSED_USED = "DISBURSED_USED";
    private static final String SLIP_CLOSED_USED = "CLOSED_USED";

    private final LoanRepository loanRepository;
    private final EntityManager entityManager;
    private final RestTemplate restTemplate;
    private final LoanEventPublisher loanEventPublisher;
    private final ProgramServiceInvoiceSubProgramValidator invoiceSubProgramValidator;
    private final ProgramServiceSubProgramLimits subProgramLimits;
    private final ProgramServiceProgramConfigClient programServiceProgramConfigClient;
    private final EligibilityService eligibilityService;
    private final AuditService auditService;
    private final ProgramServiceSalarySlipClient salarySlipClient;
    private final PlpLmsOrchestrator plpLmsOrchestrator;

    @Transactional
    public Loan requestLoan(Loan loan) {
        if ("PAY_DAY_LOAN".equals(loan.getProductType()) && loan.getProgramId() == null) {
            UUID derivedProgramId = fetchProgramIdForBorrower(loan.getBorrowerId());
            if (derivedProgramId == null) {
                throw new RuntimeException(
                        "Unable to resolve program for borrower " + loan.getBorrowerId() + ". Please contact support.");
            }
            loan.setProgramId(derivedProgramId);
            log.info(
                    "Derived programId for PAY_DAY_LOAN from borrower borrowerId={} programId={}",
                    loan.getBorrowerId(),
                    derivedProgramId);
        }

        if ("INVOICE_DISCOUNTING".equals(loan.getProductType()) && loan.getInvoiceId() != null) {
            if (loanRepository.existsByInvoiceIdAndStatusIn(loan.getInvoiceId(), INVOICE_DISCOUNTING_OPEN_LOAN_STATUSES)) {
                throw new LendingBusinessException(HttpStatus.CONFLICT, MSG_INVOICE_DISCOUNTING_ALREADY_REQUESTED);
            }
            if (loan.getProgramId() == null) {
                resolveInvoiceDiscountingProgramAndSubProgram(loan);
            } else {
                resolveInvoiceDiscountingTermsWhenProgramKnown(loan);
            }
        }

        if (loan.getAnchorId() == null && loan.getProgramId() != null) {
            try {
                HttpEntity<Void> psEntity = new HttpEntity<>(ProgramServiceAuthHeaders.trustedInternalHeaders());
                @SuppressWarnings("unchecked")
                Map<String, Object> programResponse = restTemplate.exchange(
                                "http://program-service/api/v1/programs/{programId}",
                                HttpMethod.GET,
                                psEntity,
                                Map.class,
                                loan.getProgramId())
                        .getBody();
                if (programResponse != null && "SUCCESS".equals(programResponse.get("status"))) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> programData = (Map<String, Object>) programResponse.get("data");
                    if (programData != null && programData.containsKey("anchorId")) {
                        loan.setAnchorId(UUID.fromString(programData.get("anchorId").toString()));
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to resolve anchorId from program {}: {}", loan.getProgramId(), e.getMessage());
            }
            if (loan.getAnchorId() == null) {
                throw new RuntimeException("Unable to resolve anchor for program " + loan.getProgramId() + ". Please try again or contact support.");
            }
        }

        if ("PAY_DAY_LOAN".equals(loan.getProductType())) {
            Map<String, Object> eligibilityResult = eligibilityService.checkPayDayLoanEligibility(
                    loan.getBorrowerId(), loan.getProgramId(), loan.getRequestedAmount(), loan.getSalaryDataId());
            Object eligibleObj = eligibilityResult.get("eligible");
            boolean eligible = Boolean.TRUE.equals(eligibleObj);
            if (!eligible) {
                String reason = "Unknown reason";
                @SuppressWarnings("unchecked")
                List<String> reasons = (List<String>) eligibilityResult.get("reasons");
                if (reasons != null && !reasons.isEmpty()) {
                    reason = reasons.getFirst();
                }
                throw new RuntimeException("Borrower is not eligible for Pay Day Loan: " + reason);
            }
            UUID salaryRecordId;
            if (loan.getSalaryDataId() != null) {
                assertPayDaySalarySlipSelectable(loan.getBorrowerId(), loan.getProgramId(), loan.getSalaryDataId());
                salaryRecordId = loan.getSalaryDataId();
            } else {
                salaryRecordId = fetchPrimarySalaryDataId(loan.getBorrowerId());
            }
            if (salaryRecordId == null) {
                throw new RuntimeException("Unable to resolve salary record for Pay Day Loan");
            }
            loan.setSalaryDataId(salaryRecordId);
            log.info(
                    "PAY_DAY_LOAN request using salaryDataId={} borrowerId={} programId={}",
                    salaryRecordId,
                    loan.getBorrowerId(),
                    loan.getProgramId());
            if (loanRepository.existsBySalaryDataIdAndStatusIn(salaryRecordId, PAY_DAY_SALARY_ACTIVE_STATUSES)) {
                log.warn(
                        "PAY_DAY_LOAN blocked: salary period already has a tying loan salaryDataId={} borrowerId={}",
                        salaryRecordId,
                        loan.getBorrowerId());
                throw new LendingBusinessException(HttpStatus.CONFLICT, MSG_PAY_DAY_LOAN_EXISTS_FOR_SALARY_PERIOD);
            }

            // Always resolve sub-program terms for Pay Day Loan so tenure/interests are authoritative when client omits them.
            PayLoanSubProgramTerms resolvedTerms =
                    resolvePayLoanSubProgramTerms(loan.getBorrowerId(), loan.getProgramId(), loan.getRequestedAmount())
                            .orElse(null);

            if (resolvedTerms == null || resolvedTerms.subProgramId() == null) {
                throw RestClientIntegrationMapper.noActivePayLoanSubProgram();
            }
            loan.setSubProgramId(resolvedTerms.subProgramId());

            Optional<ProgramServiceSubProgramLimits.DualSubProgramBorrowerAvailability> dualAtRequest =
                    subProgramLimits.fetchDualLimitHeadroom(loan.getSubProgramId(), loan.getBorrowerId());
            if (dualAtRequest.isEmpty()) {
                throw new LendingBusinessException(HttpStatus.BAD_REQUEST, MSG_PAY_DAY_LOAN_LIMIT_INSUFFICIENT);
            }
            ProgramServiceSubProgramLimits.DualSubProgramBorrowerAvailability payDayLimits = dualAtRequest.get();
            BigDecimal borrowerAvailable = payDayLimits.borrowerAvailable();
            BigDecimal subProgramAvailable = payDayLimits.subProgramAvailable();
            log.info(
                    "PAY LOAN LIMIT CHECK: borrowerAvailable={}, subProgramAvailable={}, requested={}",
                    borrowerAvailable,
                    subProgramAvailable,
                    loan.getRequestedAmount());
            if (payDayLimits.effectiveAvailable().compareTo(loan.getRequestedAmount()) < 0) {
                throw new LendingBusinessException(HttpStatus.BAD_REQUEST, MSG_PAY_DAY_LOAN_LIMIT_INSUFFICIENT);
            }

            if (loan.getInterestRate() == null
                    && resolvedTerms != null
                    && resolvedTerms.interestRate() != null) {
                loan.setInterestRate(resolvedTerms.interestRate());
            }

            if (loan.getTenureDays() == null || loan.getTenureDays() <= 0) {
                if (resolvedTerms.maxTenureDays() != null && resolvedTerms.maxTenureDays() > 0) {
                    loan.setTenureDays(resolvedTerms.maxTenureDays());
                } else {
                    loan.setTenureDays(30);
                }
            }

            log.info(
                    "DEBUG PAY LOAN: borrowerId={}, programId={}, tenure={}, interest={}, resolvedMaxTenure={}",
                    loan.getBorrowerId(),
                    loan.getProgramId(),
                    loan.getTenureDays(),
                    loan.getInterestRate(),
                    resolvedTerms.maxTenureDays());

            if (loan.getTenureDays() == null || loan.getTenureDays() <= 0) {
                throw new LendingBusinessException(HttpStatus.BAD_REQUEST, MSG_LOAN_TENURE_NOT_CONFIGURED);
            }
        }

        if ("INVOICE_DISCOUNTING".equals(loan.getProductType()) && loan.getInvoiceId() != null) {
            validateInvoiceEligibleForFinancing(
                    loan.getInvoiceId(), loan.getBorrowerId(), loan.getProgramId(), loan.getRequestedAmount());
            if (loan.getSubProgramId() == null) {
                loan.setSubProgramId(subProgramLimits.fetchSubProgramIdFromInvoice(loan.getInvoiceId()));
            }
            if (loan.getSubProgramId() == null) {
                throw RestClientIntegrationMapper.noSubProgramForInvoiceDiscounting();
            }
        }

        loan.setLoanNumber(generateLoanNumber(loan.getProductType()));
        loan.setRequestDate(LocalDate.now());
        loan.setStatus(LoanStatus.REQUESTED);
        loan.setTotalRepaid(BigDecimal.ZERO);
        loan.setPenalInterest(BigDecimal.ZERO);
        loan.setDpd(0);

        if (loan.getInterestRate() == null) {
            loan.setInterestRate(BigDecimal.ZERO);
        }

        int tenureDaysForInterest = mandatoryTenureDaysPrimitive(loan);

        BigDecimal interest = calculateInterest(
                loan.getRequestedAmount(),
                loan.getInterestRate(),
                tenureDaysForInterest
        );
        loan.setInterestAmount(interest);

        BigDecimal processingFee = loan.getProcessingFee() != null ? loan.getProcessingFee() : BigDecimal.ZERO;
        loan.setTotalRepayable(loan.getRequestedAmount().add(interest).add(processingFee));
        loan.setOutstandingAmount(loan.getTotalRepayable());

        if ("INVOICE_DISCOUNTING".equals(loan.getProductType()) && loan.getInvoiceId() != null) {
            UUID invId = loan.getInvoiceId();
            log.info(
                    "Invoice discounting loan ready for persistence; calling program-service mark-financing-requested invoiceId={}",
                    invId);
            markInvoiceFinancingRequested(invId);
            log.info(
                    "mark-financing-requested succeeded for invoiceId={}; persisting loan loanNumber={}",
                    invId,
                    loan.getLoanNumber());
        }

        loan = loanRepository.save(loan);
        if ("PAY_DAY_LOAN".equals(loan.getProductType()) && loan.getSalaryDataId() != null) {
            salarySlipClient.patchSlipStatus(loan.getSalaryDataId(), SLIP_LOAN_REQUESTED);
        }
        if ("INVOICE_DISCOUNTING".equals(loan.getProductType()) && loan.getInvoiceId() != null) {
            log.info(
                    "Loan persisted for invoice invoiceId={} loanId={} loanNumber={}",
                    loan.getInvoiceId(),
                    loan.getId(),
                    loan.getLoanNumber());
        }
        log.info("Loan requested: {} by borrower {} amount {}", loan.getLoanNumber(), loan.getBorrowerId(), loan.getRequestedAmount());
        loanEventPublisher.publishLoanEvent("LOAN_REQUESTED", loan);
        loanEventPublisher.publishAuditEvent("LOAN", loan.getId().toString(), "REQUESTED",
                null, null, null, "{\"loanNumber\":\"" + loan.getLoanNumber() + "\",\"amount\":" + loan.getRequestedAmount() + "}");
        return loan;
    }

    @Transactional
    public Loan approveLoan(UUID loanId, UUID approvedBy, BigDecimal sanctionedAmount) {
        Loan loan = getLoanForUpdate(loanId);
        if (loan.getStatus() != LoanStatus.REQUESTED && loan.getStatus() != LoanStatus.ELIGIBILITY_CHECK) {
            throw new RuntimeException("Loan cannot be sanctioned. Current status: " + loan.getStatus());
        }

        loan.setSanctionedAmount(sanctionedAmount != null ? sanctionedAmount : loan.getRequestedAmount());
        loan.setApprovedBy(approvedBy);
        loan.setSanctionDate(LocalDate.now());
        loan.setStatus(LoanStatus.SANCTIONED);
        loan.setDueDate(LocalDate.now().plusDays(loan.getTenureDays()));

        BigDecimal interest = calculateInterest(loan.getSanctionedAmount(), loan.getInterestRate(), loan.getTenureDays());
        loan.setInterestAmount(interest);
        BigDecimal fee = loan.getProcessingFee() != null ? loan.getProcessingFee() : BigDecimal.ZERO;
        loan.setTotalRepayable(loan.getSanctionedAmount().add(interest).add(fee));
        loan.setOutstandingAmount(loan.getTotalRepayable());

        loanRepository.save(loan);
        log.info("Loan sanctioned: {} sanctionedAmount={}", loan.getLoanNumber(), loan.getSanctionedAmount());
        try {
            plpLmsOrchestrator.onLoanSanctioned(loan);
        } catch (Exception e) {
            log.error("PLP LMS sanction hook failed for {} (non-blocking): {}", loan.getLoanNumber(), e.getMessage(), e);
        }
        loanEventPublisher.publishLoanEvent("LOAN_APPROVED", loan);
        loanEventPublisher.publishAuditEvent("LOAN", loan.getId().toString(), "SANCTIONED",
                approvedBy != null ? approvedBy.toString() : null, null,
                "{\"status\":\"REQUESTED\"}", "{\"status\":\"SANCTIONED\",\"sanctionedAmount\":" + loan.getSanctionedAmount() + "}");
        return loan;
    }

    @Transactional
    public Loan rejectLoan(UUID loanId, String reason, UUID rejectedBy) {
        Loan loan = getLoanForUpdate(loanId);
        if (loan.getStatus() != LoanStatus.REQUESTED && loan.getStatus() != LoanStatus.ELIGIBILITY_CHECK) {
            throw new RuntimeException("Loan cannot be rejected. Current status: " + loan.getStatus());
        }
        loan.setStatus(LoanStatus.REJECTED);
        loan.setRejectionReason(reason);
        loanRepository.save(loan);
        if ("PAY_DAY_LOAN".equals(loan.getProductType()) && loan.getSalaryDataId() != null) {
            salarySlipClient.patchSlipStatus(loan.getSalaryDataId(), SLIP_REJECTED_AGAIN);
        }
        log.info("Loan rejected: {} reason={}", loan.getLoanNumber(), reason);
        loanEventPublisher.publishAuditEvent("LOAN", loan.getId().toString(), "REJECTED",
                rejectedBy != null ? rejectedBy.toString() : null, null,
                "{\"status\":\"REQUESTED\"}", "{\"status\":\"REJECTED\",\"reason\":\"" + reason + "\"}");
        return loan;
    }

    /**
     * Treasury / admin: move sanctioned loan to pending disbursement (no limits, no invoice updates).
     */
    @Transactional
    public Loan initiateDisbursement(UUID loanId, BigDecimal amount, UUID initiatedBy) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Amount must be positive");
        }
        Loan loan = getLoanForUpdate(loanId);
        if (loan.getStatus() != LoanStatus.SANCTIONED) {
            throw new RuntimeException("Loan cannot initiate disbursement. Current status: " + loan.getStatus());
        }
        BigDecimal cap = loan.getSanctionedAmount() != null ? loan.getSanctionedAmount() : loan.getRequestedAmount();
        if (amount.compareTo(cap) > 0) {
            throw new RuntimeException("Initiated amount exceeds sanctioned amount");
        }

        Map<String, Object> snap = loan.getEligibilitySnapshot();
        Map<String, Object> mutable = snap == null ? new HashMap<>() : new HashMap<>(snap);
        mutable.put("pendingDisbursementAmount", amount.toPlainString());
        loan.setEligibilitySnapshot(mutable);
        loan.setStatus(LoanStatus.DISBURSEMENT_PENDING);

        loanRepository.save(loan);
        log.info("Disbursement initiated: {} amount={}", loan.getLoanNumber(), amount);
        loanEventPublisher.publishAuditEvent(
                "LOAN",
                loan.getId().toString(),
                "DISBURSEMENT_INITIATED",
                initiatedBy != null ? initiatedBy.toString() : null,
                null,
                "{\"status\":\"SANCTIONED\"}",
                "{\"status\":\"DISBURSEMENT_PENDING\",\"pendingDisbursementAmount\":" + amount.toPlainString() + "}");
        return loan;
    }

    /**
     * Treasury: withdraw from disbursement initiation. Program-service sub-program / borrower limits are not booked
     * until {@link #markDisbursed} succeeds, so there is no limit release step for the standard pending state.
     */
    @Transactional
    public Loan cancelDisbursement(UUID loanId) {
        Loan loan = getLoanForUpdate(loanId);
        if (loan.getStatus() != LoanStatus.DISBURSEMENT_PENDING) {
            throw new LendingBusinessException(
                    HttpStatus.BAD_REQUEST, "Only disbursement pending loans can be cancelled");
        }

        clearPendingDisbursementSnapshot(loan);

        if (loan.getInvoiceId() != null && "INVOICE_DISCOUNTING".equals(loan.getProductType())) {
            revertInvoiceFinancingRequested(loan.getInvoiceId());
        }

        loan.setStatus(LoanStatus.CANCELLED);
        loan.setRejectionReason("Disbursement cancelled");

        loanRepository.save(loan);
        if ("PAY_DAY_LOAN".equals(loan.getProductType()) && loan.getSalaryDataId() != null) {
            salarySlipClient.patchSlipStatus(loan.getSalaryDataId(), SLIP_REJECTED_AGAIN);
        }
        log.info("Disbursement cancelled loanNumber={}", loan.getLoanNumber());
        loanEventPublisher.publishAuditEvent(
                "LOAN",
                loan.getId().toString(),
                "DISBURSEMENT_CANCELLED",
                null,
                null,
                "{\"status\":\"DISBURSEMENT_PENDING\"}",
                "{\"status\":\"CANCELLED\",\"reason\":\"Disbursement cancelled\"}");
        loanEventPublisher.publishLoanEvent("DISBURSEMENT_CANCELLED", loan);
        return loan;
    }

    @Transactional
    public Loan markDisbursed(UUID loanId, BigDecimal disbursedAmount, UUID executedBy) {
        Loan loan = getLoanForUpdate(loanId);
        if (loan.getStatus() != LoanStatus.DISBURSEMENT_PENDING) {
            throw new RuntimeException(LenderRoleAuthorization.MSG_PENDING_DISBURSE);
        }
        BigDecimal pending = extractPendingDisbursementAmount(loan);
        if (pending == null) {
            throw new RuntimeException(LenderRoleAuthorization.MSG_PENDING_DISBURSE);
        }
        if (disbursedAmount.compareTo(pending) != 0) {
            throw new RuntimeException("Disbursement amount must match initiated amount: " + pending.toPlainString());
        }
        loan.setDisbursedAmount(disbursedAmount);
        loan.setDisbursementDate(LocalDate.now());
        loan.setStatus(LoanStatus.DISBURSED);
        loan.setDueDate(LocalDate.now().plusDays(loan.getTenureDays()));

        BigDecimal interest = calculateInterest(disbursedAmount, loan.getInterestRate(), loan.getTenureDays());
        loan.setInterestAmount(interest);
        BigDecimal fee = loan.getProcessingFee() != null ? loan.getProcessingFee() : BigDecimal.ZERO;
        loan.setTotalRepayable(disbursedAmount.add(interest).add(fee));
        loan.setOutstandingAmount(loan.getTotalRepayable());

        // Save loan state first — ensures DB commit succeeds before remote calls
        loanRepository.save(loan);
        entityManager.flush();
        log.info("Loan disbursed (local): {} amount={}", loan.getLoanNumber(), disbursedAmount);

        assertModernProductLinkageBeforeBlockingLimits(loan);

        UUID limitSubProgramId = resolveLimitSubProgramIdForDisbursement(loan);
        boolean limitViaSubProgram = limitSubProgramId != null;

        if (limitViaSubProgram) {
            try {
                subProgramLimits.blockSubProgramLimits(limitSubProgramId, loan.getBorrowerId(), disbursedAmount);
                log.info("Sub-program limit blocked subProgram={} borrower={} amount={}",
                        limitSubProgramId, loan.getBorrowerId(), disbursedAmount);
            } catch (LendingBusinessException e) {
                log.error("Failed to block sub-program limit — reverting disbursement: {}", e.getReason());
                revertDisbursementPendingState(loan);
                throw e;
            }
        } else {
            if ("PAY_DAY_LOAN".equals(loan.getProductType())
                    || "INVOICE_DISCOUNTING".equals(loan.getProductType())) {
                if (loan.isLegacyProgramLevelLimits()) {
                    log.info(
                            "Disbursement using legacy program borrower_limits loanNumber={} productType={} borrower={} program={}",
                            loan.getLoanNumber(),
                            loan.getProductType(),
                            loan.getBorrowerId(),
                            loan.getProgramId());
                } else {
                    log.error(
                            "BUG: borrower_limits disbursement reached for Pay Day / invoice loan without legacy flag loanNumber={} productType={}",
                            loan.getLoanNumber(),
                            loan.getProductType());
                }
            }
            try {
                blockBorrowerProgramLimit(loan, disbursedAmount);
                log.info(
                        "Borrower limit blocked for borrower={} program={} amount={}",
                        loan.getBorrowerId(),
                        loan.getProgramId(),
                        disbursedAmount);
            } catch (LendingBusinessException e) {
                log.error("Failed to block borrower limit — reverting disbursement: {}", e.getReason());
                revertDisbursementPendingState(loan);
                throw e;
            }
        }

        // Mark invoice as discounted for INVOICE_DISCOUNTING loans
        if (loan.getInvoiceId() != null && "INVOICE_DISCOUNTING".equals(loan.getProductType())) {
            try {
                HttpEntity<Map<String, BigDecimal>> markEntity =
                        new HttpEntity<>(Map.of("amount", disbursedAmount), ProgramServiceAuthHeaders.trustedInternalJsonHeaders());
                restTemplate.exchange(
                        "http://program-service/api/v1/invoices/{invoiceId}/mark-discounted",
                        HttpMethod.POST,
                        markEntity,
                        Map.class,
                        loan.getInvoiceId());
                log.info("Invoice {} marked discounted amount={}", loan.getInvoiceId(), disbursedAmount);
            } catch (Exception e) {
                compensateBlockedLimitsAfterInvoiceMarkFailure(loan, disbursedAmount, limitViaSubProgram, limitSubProgramId);
                revertDisbursementPendingState(loan);
                log.error("Failed to mark invoice {} as discounted — aborting disbursement: {}", loan.getInvoiceId(), e.getMessage());
                if (e instanceof RestClientResponseException rce) {
                    throw RestClientIntegrationMapper.toBusinessException(
                            rce, "Unable to update invoice discounted amount");
                }
                throw new LendingBusinessException(
                        HttpStatus.BAD_GATEWAY, "Unable to update invoice discounted amount: " + e.getMessage());
            }
        }

        clearPendingDisbursementSnapshot(loan);
        loanRepository.save(loan);

        try {
            plpLmsOrchestrator.onLoanDisbursed(loan);
        } catch (Exception e) {
            log.error("PLP LMS disburse hook failed for {} (non-blocking): {}", loan.getLoanNumber(), e.getMessage(), e);
        }

        log.info("Loan disbursed: {} amount={}", loan.getLoanNumber(), disbursedAmount);
        loanEventPublisher.publishLoanEvent("LOAN_DISBURSED", loan);
        loanEventPublisher.publishAuditEvent("LOAN", loan.getId().toString(), "DISBURSED",
                executedBy != null ? executedBy.toString() : null, null,
                "{\"status\":\"DISBURSEMENT_PENDING\"}", "{\"status\":\"DISBURSED\",\"disbursedAmount\":" + disbursedAmount + "}");
        if ("PAY_DAY_LOAN".equals(loan.getProductType()) && loan.getSalaryDataId() != null) {
            salarySlipClient.patchSlipStatus(loan.getSalaryDataId(), SLIP_DISBURSED_USED);
        }
        return loan;
    }

    @Transactional
    public Loan recordRepayment(UUID loanId, BigDecimal repaidAmount) {
        Loan loan = getLoanForUpdate(loanId);
        if (loan.getStatus() != LoanStatus.DISBURSED && loan.getStatus() != LoanStatus.REPAYMENT_DUE && loan.getStatus() != LoanStatus.OVERDUE) {
            throw new RuntimeException("Repayment cannot be recorded. Loan status: " + loan.getStatus());
        }
        loan.setTotalRepaid(loan.getTotalRepaid().add(repaidAmount));
        loan.setOutstandingAmount(loan.getTotalRepayable().subtract(loan.getTotalRepaid()));

        if (loan.getOutstandingAmount().compareTo(BigDecimal.ZERO) <= 0) {
            loan.setOutstandingAmount(BigDecimal.ZERO);
            loan.setStatus(LoanStatus.CLOSED);
            loan.setClosureDate(LocalDate.now());
            log.info("Loan closed: {}", loan.getLoanNumber());

            UUID limitSp = resolveLimitSubProgramIdForDisbursement(loan);
            if (limitSp != null && loan.getDisbursedAmount() != null) {
                try {
                    subProgramLimits.releaseSubProgramLimits(limitSp, loan.getBorrowerId(), loan.getDisbursedAmount());
                    log.info("Sub-program limit released subProgram={} borrower={} amount={}",
                            limitSp, loan.getBorrowerId(), loan.getDisbursedAmount());
                } catch (Exception e) {
                    log.error("CRITICAL: Failed sub-program limit release subProgram={} borrower={} amount={}: {}",
                            limitSp, loan.getBorrowerId(), loan.getDisbursedAmount(), e.getMessage());
                    loanEventPublisher.publishLoanEvent("LIMIT_RELEASE_REQUIRED", loan);
                }
            } else if (loan.getDisbursedAmount() != null) {
                boolean modernPayOrInvoice = "PAY_DAY_LOAN".equals(loan.getProductType())
                        || "INVOICE_DISCOUNTING".equals(loan.getProductType());
                if (!modernPayOrInvoice) {
                    log.info(
                            "Program borrower_limits release (non-sub-program product) loanNumber={} productType={}",
                            loan.getLoanNumber(),
                            loan.getProductType());
                } else if (loan.isLegacyProgramLevelLimits()) {
                    log.info(
                            "Legacy program borrower_limits release loanNumber={} productType={} legacyProgramLevelLimits=true",
                            loan.getLoanNumber(),
                            loan.getProductType());
                } else {
                    log.warn(
                            "Modern Pay Day / invoice loan released via program borrower_limits (sub-program absent at disburse) — repair subProgramId recommended loanNumber={} productType={}",
                            loan.getLoanNumber(),
                            loan.getProductType());
                }
                try {
                    HttpEntity<Void> psPost = new HttpEntity<>(ProgramServiceAuthHeaders.trustedInternalHeaders());
                    restTemplate.exchange(
                            "http://program-service/api/v1/borrowers/{borrowerId}/limits/release?programId={programId}&amount={amount}",
                            HttpMethod.POST,
                            psPost,
                            Map.class,
                            loan.getBorrowerId(),
                            loan.getProgramId(),
                            loan.getDisbursedAmount());
                    log.info("Limit released for borrower={} program={} amount={}", loan.getBorrowerId(), loan.getProgramId(), loan.getDisbursedAmount());
                } catch (Exception e) {
                    log.error("CRITICAL: Failed to release limit for borrower={} program={} amount={}: {}. Publishing compensating event for retry.",
                            loan.getBorrowerId(), loan.getProgramId(), loan.getDisbursedAmount(), e.getMessage());
                    loanEventPublisher.publishLoanEvent("LIMIT_RELEASE_REQUIRED", loan);
                }
            }
        }

        loanRepository.save(loan);
        try {
            plpLmsOrchestrator.onRepayment(loan, repaidAmount);
        } catch (Exception e) {
            log.error("PLP LMS repayment hook failed for {} (non-blocking): {}", loan.getLoanNumber(), e.getMessage(), e);
        }
        if (loan.getStatus() == LoanStatus.CLOSED
                && "PAY_DAY_LOAN".equals(loan.getProductType())
                && loan.getSalaryDataId() != null) {
            salarySlipClient.patchSlipStatus(loan.getSalaryDataId(), SLIP_CLOSED_USED);
        }
        loanEventPublisher.publishLoanEvent("REPAYMENT_RECEIVED", loan);
        loanEventPublisher.publishAuditEvent("LOAN", loan.getId().toString(), "REPAYMENT",
                null, null, null, "{\"repaidAmount\":" + repaidAmount + ",\"outstanding\":" + loan.getOutstandingAmount() + ",\"status\":\"" + loan.getStatus() + "\"}");
        return loan;
    }

    /**
     * Dev/admin: backfill {@code subProgramId} on open Pay Day / invoice loans where it was never persisted.
     */
    @Transactional
    public Map<String, Object> repairLoanSubProgramLinks(
            String userIdHeader,
            String rolesHeader,
            String linkedEntityId,
            String linkedEntityType) {
        int repairedCount = 0;
        int skippedCount = 0;
        List<Map<String, String>> details = new ArrayList<>();
        List<UUID> ids =
                loanRepository.findOpenLoansMissingSubProgramLink().stream().map(Loan::getId).toList();

        for (UUID loanId : ids) {
            String loanNum = loanId.toString();
            try {
                Loan loan = getLoanForUpdate(loanId);
                loanNum = loan.getLoanNumber();
                if (loan.getSubProgramId() != null) {
                    details.add(repairDetail(loanNum, "SKIP", "subProgramId already set"));
                    skippedCount++;
                    continue;
                }
                String pt = loan.getProductType();
                if ("PAY_DAY_LOAN".equals(pt)) {
                    BigDecimal amt =
                            loan.getSanctionedAmount() != null
                                    ? loan.getSanctionedAmount()
                                    : loan.getRequestedAmount();
                    if (amt == null) {
                        details.add(
                                repairDetail(
                                        loanNum,
                                        "SKIP",
                                        "No sanctioned or requested amount to resolve Pay Loan sub-program"));
                        skippedCount++;
                        continue;
                    }
                    PayLoanSubProgramTerms resolved =
                            resolvePayLoanSubProgramTerms(loan.getBorrowerId(), loan.getProgramId(), amt).orElse(null);
                    if (resolved == null || resolved.subProgramId() == null) {
                        details.add(
                                repairDetail(
                                        loanNum,
                                        "SKIP",
                                        "No active Pay Loan sub-program qualifies for borrower/amount"));
                        skippedCount++;
                        continue;
                    }
                    loan.setSubProgramId(resolved.subProgramId());
                    loanRepository.save(loan);
                    logLoanSubprogramRepairedAudit(
                            loan,
                            resolved.subProgramId(),
                            userIdHeader,
                            rolesHeader,
                            linkedEntityId,
                            linkedEntityType);
                    repairedCount++;
                    details.add(
                            repairDetail(
                                    loanNum, "REPAIRED", "Set subProgramId from active Pay Loan sub-program"));
                } else if ("INVOICE_DISCOUNTING".equals(pt)) {
                    if (loan.getInvoiceId() == null) {
                        details.add(repairDetail(loanNum, "SKIP", "invoiceId absent"));
                        skippedCount++;
                        continue;
                    }
                    UUID fromInv = subProgramLimits.fetchSubProgramIdFromInvoice(loan.getInvoiceId());
                    boolean resolvedSubProgram = false;
                    if (fromInv != null) {
                        loan.setSubProgramId(fromInv);
                        resolvedSubProgram = true;
                    } else if (tryAutoResolveInvoiceDiscountingSubProgramForRepair(loan)) {
                        resolvedSubProgram = true;
                    }
                    if (!resolvedSubProgram || loan.getSubProgramId() == null) {
                        details.add(
                                repairDetail(
                                        loanNum,
                                        "SKIP",
                                        "Could not resolve sub-program from invoice or auto-resolution"));
                        skippedCount++;
                        continue;
                    }
                    loanRepository.save(loan);
                    logLoanSubprogramRepairedAudit(
                            loan,
                            loan.getSubProgramId(),
                            userIdHeader,
                            rolesHeader,
                            linkedEntityId,
                            linkedEntityType);
                    repairedCount++;
                    details.add(
                            repairDetail(
                                    loanNum, "REPAIRED", "Set subProgramId from invoice or invoice resolver"));
                } else {
                    details.add(repairDetail(loanNum, "SKIP", "Unsupported product: " + pt));
                    skippedCount++;
                }
            } catch (Exception e) {
                log.warn("repairLoanSubProgramLinks loanId={} failed: {}", loanId, e.getMessage(), e);
                skippedCount++;
                details.add(repairDetail(loanNum, "ERROR", truncateRepairReason(e)));
            }
        }

        Map<String, Object> out = new HashMap<>();
        out.put("repairedCount", repairedCount);
        out.put("skippedCount", skippedCount);
        out.put("details", details);
        return out;
    }

    /**
     * Non-legacy Pay Day / invoice flows must resolve a sub-program before blocking limits (no silent
     * {@code borrower_limits} fallback unless {@link Loan#isLegacyProgramLevelLimits()}).
     */
    private void assertModernProductLinkageBeforeBlockingLimits(Loan loan) {
        if (loan.isLegacyProgramLevelLimits()) {
            return;
        }
        if ("PAY_DAY_LOAN".equals(loan.getProductType())) {
            if (loan.getSubProgramId() == null) {
                throw new LendingBusinessException(
                        HttpStatus.BAD_REQUEST,
                        "Pay Loan is missing sub-program linkage. Please cancel and recreate the loan.");
            }
            return;
        }
        if ("INVOICE_DISCOUNTING".equals(loan.getProductType()) && loan.getInvoiceId() != null) {
            if (loan.getSubProgramId() != null) {
                return;
            }
            UUID fromInvoice = subProgramLimits.fetchSubProgramIdFromInvoice(loan.getInvoiceId());
            if (fromInvoice != null) {
                return;
            }
            throw new LendingBusinessException(
                    HttpStatus.BAD_REQUEST, "Invoice discounting loan is missing sub-program linkage.");
        }
    }

    private boolean tryAutoResolveInvoiceDiscountingSubProgramForRepair(Loan existingLoan) {
        if (existingLoan.getInvoiceId() == null || existingLoan.getBorrowerId() == null) {
            return false;
        }
        Loan probe = new Loan();
        probe.setInvoiceId(existingLoan.getInvoiceId());
        probe.setBorrowerId(existingLoan.getBorrowerId());
        probe.setRequestedAmount(
                existingLoan.getRequestedAmount() != null
                        ? existingLoan.getRequestedAmount()
                        : BigDecimal.ONE);
        probe.setProgramId(null);
        probe.setAnchorId(existingLoan.getAnchorId());
        try {
            resolveInvoiceDiscountingProgramAndSubProgram(probe);
        } catch (Exception e) {
            log.debug(
                    "repair: invoice sub-program auto-resolve failed invoiceId={}: {}",
                    existingLoan.getInvoiceId(),
                    e.getMessage());
            return false;
        }
        if (probe.getSubProgramId() == null) {
            return false;
        }
        existingLoan.setSubProgramId(probe.getSubProgramId());
        if (existingLoan.getProgramId() == null && probe.getProgramId() != null) {
            existingLoan.setProgramId(probe.getProgramId());
        }
        if (existingLoan.getAnchorId() == null && probe.getAnchorId() != null) {
            existingLoan.setAnchorId(probe.getAnchorId());
        }
        return true;
    }

    private static String truncateRepairReason(Exception e) {
        String m = e.getMessage();
        if (m == null || m.isEmpty()) {
            return e.getClass().getSimpleName();
        }
        return m.length() > 240 ? m.substring(0, 240) + "..." : m;
    }

    private static Map<String, String> repairDetail(String loanNumber, String action, String reason) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("loanNumber", loanNumber);
        row.put("action", action);
        row.put("reason", reason);
        return row;
    }

    private void logLoanSubprogramRepairedAudit(
            Loan loan,
            UUID resolvedSubProgramId,
            String userIdHeader,
            String rolesHeader,
            String linkedEntityId,
            String linkedEntityType) {
        auditService.logEvent(
                "LOAN_SUBPROGRAM_REPAIRED",
                "LOAN",
                loan.getId().toString(),
                "LOAN_SUBPROGRAM_REPAIR",
                userIdHeader,
                rolesHeader,
                linkedEntityId,
                linkedEntityType,
                "SUCCESS",
                "subProgramId=" + resolvedSubProgramId + " loanNumber=" + loan.getLoanNumber());
    }

    public Loan getLoan(UUID loanId) {
        return loanRepository.findById(loanId)
                .orElseThrow(() -> new RuntimeException("Loan not found: " + loanId));
    }

    private Loan getLoanForUpdate(UUID loanId) {
        return loanRepository.findByIdForUpdate(loanId)
                .orElseThrow(() -> new RuntimeException("Loan not found: " + loanId));
    }

    public List<Loan> getLoansByBorrower(UUID borrowerId) {
        return loanRepository.findByBorrowerId(borrowerId);
    }

    public List<Loan> getLoansByAnchor(UUID anchorId) {
        return loanRepository.findByAnchorId(anchorId);
    }

    public void validateBorrowerProgramConsistency(UUID borrowerId, UUID programId) {
        validateBorrowerProgramConsistency(borrowerId, programId, null, null, null, null);
    }

    /**
     * Ensures {@code programId} matches the borrower's program in program-service when supplied.
     */
    public void validateBorrowerProgramConsistency(
            UUID borrowerId,
            UUID programId,
            String userIdHeader,
            String rolesHeader,
            String linkedEntityId,
            String linkedEntityType) {
        if (borrowerId == null || programId == null) {
            return;
        }
        UUID borrowerProgram = fetchProgramIdForBorrower(borrowerId);
        if (borrowerProgram == null || !borrowerProgram.equals(programId)) {
            AuditBridge.accessDenied(
                    "LOAN",
                    borrowerId != null ? borrowerId.toString() : "",
                    LoanAccessGuard.MSG_ACCESS_DENIED,
                    userIdHeader,
                    rolesHeader,
                    linkedEntityId,
                    linkedEntityType);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, LoanAccessGuard.MSG_ACCESS_DENIED);
        }
    }

    public List<Loan> getLoansByProgram(UUID programId) {
        return loanRepository.findByProgramId(programId);
    }

    public List<Loan> getAllLoans() {
        return loanRepository.findAll();
    }

    public List<Loan> getOverdueLoans() {
        return loanRepository.findOverdueLoans();
    }

    private record PayLoanSubProgramTerms(BigDecimal interestRate, Integer maxTenureDays, UUID subProgramId) {}

    private record PayLoanSubProgramCandidate(
            UUID subProgramId, BigDecimal interestRate, BigDecimal effectiveLimit, Integer maxTenureDays) {}

    private static final Comparator<PayLoanSubProgramCandidate> PAY_LOAN_SUB_PROGRAM_COMPARATOR =
            Comparator.comparing(PayLoanSubProgramCandidate::interestRate, Comparator.nullsLast(BigDecimal::compareTo))
                    .thenComparing(PayLoanSubProgramCandidate::subProgramId);

    /**
     * Same sub-program ordering as {@link EligibilityService#checkPayDayLoanEligibility}: lowest interest rate,
     * then subProgramId. Only candidates with available limit &gt;= requested amount.
     */
    private Optional<PayLoanSubProgramTerms> resolvePayLoanSubProgramTerms(
            UUID borrowerId, UUID programId, BigDecimal requestedAmount) {
        if (requestedAmount == null) {
            return Optional.empty();
        }
        List<Map<String, Object>> programSubPrograms = fetchProgramSubPrograms(programId);
        List<PayLoanSubProgramCandidate> enrolled = new ArrayList<>();
        for (Map<String, Object> sp : programSubPrograms) {
            if (!isActivePayLoanSubProgram(sp)) {
                continue;
            }
            UUID spId = parseUuid(sp.get("id"));
            if (spId == null) {
                continue;
            }
            var headroom = subProgramLimits.fetchEffectiveBorrowerAvailableLimit(spId, borrowerId);
            if (headroom.isEmpty()) {
                continue;
            }
            enrolled.add(new PayLoanSubProgramCandidate(
                    spId,
                    parseBigDecimal(sp.get("interestRate")),
                    headroom.get(),
                    parsePositiveInteger(sp.get("maxTenureDays"))));
        }
        return enrolled.stream()
                .filter(c -> c.effectiveLimit().compareTo(requestedAmount) >= 0)
                .min(PAY_LOAN_SUB_PROGRAM_COMPARATOR)
                .map(c -> new PayLoanSubProgramTerms(c.interestRate(), c.maxTenureDays(), c.subProgramId()));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchProgramSubPrograms(UUID programId) {
        HttpEntity<Void> psEntity = new HttpEntity<>(ProgramServiceAuthHeaders.trustedInternalHeaders());
        Map<String, Object> resp =
                restTemplate.exchange(
                                "http://program-service/api/v1/programs/{programId}/sub-programs",
                                HttpMethod.GET,
                                psEntity,
                                Map.class,
                                programId)
                        .getBody();
        if (resp == null || !"SUCCESS".equals(resp.get("status"))) {
            return List.of();
        }
        Object data = resp.get("data");
        if (!(data instanceof List<?> rawList)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : rawList) {
            if (o instanceof Map<?, ?> m) {
                out.add((Map<String, Object>) m);
            }
        }
        return out;
    }

    private static boolean isActivePayLoanSubProgram(Map<String, Object> sp) {
        Object st = sp.get("status");
        if (st == null || !"ACTIVE".equals(st.toString().trim())) {
            return false;
        }
        Object ft = sp.get("flowType");
        if (ft == null) {
            return false;
        }
        String t = ft.toString().trim().toUpperCase();
        return FLOW_PAY_LOAN.equals(t) || FLOW_PAY_DAY_LOAN.equals(t);
    }

    private int mandatoryTenureDaysPrimitive(Loan loan) {
        Integer tenure = loan.getTenureDays();
        if (tenure == null || tenure <= 0) {
            throw new LendingBusinessException(HttpStatus.BAD_REQUEST, MSG_LOAN_TENURE_NOT_CONFIGURED);
        }
        return tenure;
    }

    private BigDecimal calculateInterest(BigDecimal principal, BigDecimal annualRate, int days) {
        if (annualRate == null || annualRate.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return principal
                .multiply(annualRate)
                .multiply(BigDecimal.valueOf(days))
                .divide(BigDecimal.valueOf(36500), 2, RoundingMode.HALF_UP);
    }

    /**
     * Sub-program to block/release limits: persisted loan row wins, else invoice JSON link.
     */
    private UUID resolveEffectiveInvoiceSubProgramId(Loan loan) {
        if (loan.getSubProgramId() != null) {
            return loan.getSubProgramId();
        }
        return subProgramLimits.fetchSubProgramIdFromInvoice(loan.getInvoiceId());
    }

    /**
     * Pay Day uses {@link Loan#getSubProgramId()} (set at request). Invoice discounting uses persisted loan row or
     * invoice JSON link. When null, disbursement uses program-level {@code borrower_limits}; for Pay Day / invoice that
     * path is restricted to loans with {@link Loan#isLegacyProgramLevelLimits()}, otherwise {@link #assertModernProductLinkageBeforeBlockingLimits}.
     */
    private UUID resolveLimitSubProgramIdForDisbursement(Loan loan) {
        if ("PAY_DAY_LOAN".equals(loan.getProductType())) {
            return loan.getSubProgramId();
        }
        if ("INVOICE_DISCOUNTING".equals(loan.getProductType())) {
            return resolveEffectiveInvoiceSubProgramId(loan);
        }
        return null;
    }

    private void revertDisbursementPendingState(Loan loan) {
        loan.setStatus(LoanStatus.DISBURSEMENT_PENDING);
        loan.setDisbursedAmount(null);
        loan.setDisbursementDate(null);
        loanRepository.save(loan);
    }

    private void blockBorrowerProgramLimit(Loan loan, BigDecimal amount) {
        try {
            HttpEntity<Void> psPost = new HttpEntity<>(ProgramServiceAuthHeaders.trustedInternalHeaders());
            restTemplate.exchange(
                    "http://program-service/api/v1/borrowers/{borrowerId}/limits/block?programId={programId}&amount={amount}",
                    HttpMethod.POST,
                    psPost,
                    Map.class,
                    loan.getBorrowerId(),
                    loan.getProgramId(),
                    amount);
        } catch (RestClientResponseException e) {
            throw RestClientIntegrationMapper.toBusinessException(e, "Unable to block borrower limit");
        }
    }

    private void compensateBlockedLimitsAfterInvoiceMarkFailure(
            Loan loan, BigDecimal amount, boolean limitViaSubProgram, UUID limitSubProgramId) {
        if (limitViaSubProgram && limitSubProgramId != null) {
            try {
                subProgramLimits.releaseSubProgramLimits(limitSubProgramId, loan.getBorrowerId(), amount);
                log.info(
                        "Compensating sub-program limit release after invoice mark failure subProgram={} amount={}",
                        limitSubProgramId,
                        amount);
            } catch (Exception subRelEx) {
                log.error(
                        "CRITICAL: Failed sub-program limit release after invoice mark failure: {}",
                        subRelEx.getMessage());
            }
            return;
        }
        try {
            HttpEntity<Void> psPost = new HttpEntity<>(ProgramServiceAuthHeaders.trustedInternalHeaders());
            restTemplate.exchange(
                    "http://program-service/api/v1/borrowers/{borrowerId}/limits/release?programId={programId}&amount={amount}",
                    HttpMethod.POST,
                    psPost,
                    Map.class,
                    loan.getBorrowerId(),
                    loan.getProgramId(),
                    amount);
            log.info("Compensating borrower limit release after invoice mark failure borrower={} amount={}",
                    loan.getBorrowerId(), amount);
        } catch (Exception releaseEx) {
            log.error("CRITICAL: Failed borrower limit release after invoice mark failure: {}",
                    releaseEx.getMessage());
        }
    }

    /**
     * When {@code programId} is omitted for invoice discounting, derive {@code programId} and {@code subProgramId}
     * from the invoice anchor/program and eligible ACTIVE sub-programs (limits + enrollment).
     */
    private void resolveInvoiceDiscountingProgramAndSubProgram(Loan loan) {
        Map<String, Object> invoice = fetchInvoiceJson(loan.getInvoiceId());
        UUID invBorrower = parseUuid(invoice.get("borrowerId"));
        UUID invAnchor = parseUuid(invoice.get("anchorId"));
        UUID invProgram = parseUuid(invoice.get("programId"));
        if (invBorrower == null || invAnchor == null || invProgram == null) {
            throw new RuntimeException("Invoice is missing borrowerId, anchorId, or programId");
        }
        if (!invBorrower.equals(loan.getBorrowerId())) {
            throw new RuntimeException("Loan borrower does not match invoice borrower");
        }
        if (loan.getAnchorId() != null && !loan.getAnchorId().equals(invAnchor)) {
            throw new RuntimeException("Loan anchor does not match invoice anchor");
        }
        loan.setAnchorId(invAnchor);

        BigDecimal requested = loan.getRequestedAmount();
        if (requested == null) {
            throw new RuntimeException("requestedAmount is required for invoice discounting");
        }

        UUID invoiceSubProgramHint = parseUuid(invoice.get("subProgramId"));

        List<Map<String, Object>> subPrograms = listAllSubProgramMaps();
        List<SubProgramCandidate> eligible = new ArrayList<>();
        for (Map<String, Object> sp : subPrograms) {
            Object statusObj = sp.get("status");
            if (statusObj == null || !"ACTIVE".equals(statusObj.toString().trim())) {
                continue;
            }
            UUID spId = parseUuid(sp.get("id"));
            UUID spAnchor = parseUuid(sp.get("anchorId"));
            UUID spProgram = parseUuid(sp.get("programId"));
            if (spId == null || spAnchor == null || spProgram == null) {
                continue;
            }
            if (invoiceSubProgramHint != null && !invoiceSubProgramHint.equals(spId)) {
                continue;
            }
            if (!invAnchor.equals(spAnchor) || !invProgram.equals(spProgram)) {
                continue;
            }
            if (!isBorrowerEnrolledInSubProgram(spId, invBorrower)) {
                continue;
            }
            if (subProgramLimits.validateRequestedAmountWithinSubProgram(spId, invBorrower, requested).isPresent()) {
                continue;
            }
            eligible.add(new SubProgramCandidate(
                    spId,
                    spProgram,
                    parseBigDecimal(sp.get("interestRate")),
                    parsePositiveInteger(sp.get("maxTenureDays"))));
        }

        eligible.sort(Comparator.comparing(SubProgramCandidate::interestRate, Comparator.nullsLast(BigDecimal::compareTo))
                .thenComparing(SubProgramCandidate::id));

        if (eligible.isEmpty()) {
            throw new RuntimeException("No eligible program found for invoice discounting");
        }

        SubProgramCandidate chosen = eligible.get(0);
        loan.setProgramId(chosen.programId());
        loan.setSubProgramId(chosen.id());
        applyResolvedInvoiceDiscountingLoanTerms(loan, chosen, invoice);
        log.info(
                "Resolved invoice discounting program/subProgram from invoice invoiceId={} borrowerId={} programId={} subProgramId={}",
                loan.getInvoiceId(),
                loan.getBorrowerId(),
                chosen.programId(),
                chosen.id());
    }

    /**
     * When {@code programId} is already on the loan payload, still resolve sub-program, tenure, and interest from the
     * invoice and ACTIVE sub-program enrollment (same eligibility rules as {@link #resolveInvoiceDiscountingProgramAndSubProgram}).
     */
    private void resolveInvoiceDiscountingTermsWhenProgramKnown(Loan loan) {
        Map<String, Object> invoice = fetchInvoiceJson(loan.getInvoiceId());
        UUID invBorrower = parseUuid(invoice.get("borrowerId"));
        UUID invAnchor = parseUuid(invoice.get("anchorId"));
        UUID invProgram = parseUuid(invoice.get("programId"));
        if (invBorrower == null || invAnchor == null || invProgram == null) {
            throw new RuntimeException("Invoice is missing borrowerId, anchorId, or programId");
        }
        if (!invBorrower.equals(loan.getBorrowerId())) {
            throw new RuntimeException("Loan borrower does not match invoice borrower");
        }
        if (!invProgram.equals(loan.getProgramId())) {
            throw new RuntimeException("Loan program does not match invoice program");
        }
        if (loan.getAnchorId() != null && !loan.getAnchorId().equals(invAnchor)) {
            throw new RuntimeException("Loan anchor does not match invoice anchor");
        }
        loan.setAnchorId(invAnchor);

        BigDecimal requested = loan.getRequestedAmount();
        if (requested == null) {
            throw new RuntimeException("requestedAmount is required for invoice discounting");
        }

        UUID invoiceSubProgramHint = parseUuid(invoice.get("subProgramId"));
        UUID subProgramHint =
                loan.getSubProgramId() != null ? loan.getSubProgramId() : invoiceSubProgramHint;

        List<Map<String, Object>> subPrograms = listAllSubProgramMaps();
        List<SubProgramCandidate> eligible = new ArrayList<>();
        for (Map<String, Object> sp : subPrograms) {
            Object statusObj = sp.get("status");
            if (statusObj == null || !"ACTIVE".equals(statusObj.toString().trim())) {
                continue;
            }
            UUID spId = parseUuid(sp.get("id"));
            UUID spAnchor = parseUuid(sp.get("anchorId"));
            UUID spProgram = parseUuid(sp.get("programId"));
            if (spId == null || spAnchor == null || spProgram == null) {
                continue;
            }
            if (subProgramHint != null && !subProgramHint.equals(spId)) {
                continue;
            }
            if (!invAnchor.equals(spAnchor) || !invProgram.equals(spProgram)) {
                continue;
            }
            if (!isBorrowerEnrolledInSubProgram(spId, invBorrower)) {
                continue;
            }
            if (subProgramLimits.validateRequestedAmountWithinSubProgram(spId, invBorrower, requested).isPresent()) {
                continue;
            }
            eligible.add(new SubProgramCandidate(
                    spId,
                    spProgram,
                    parseBigDecimal(sp.get("interestRate")),
                    parsePositiveInteger(sp.get("maxTenureDays"))));
        }

        eligible.sort(Comparator.comparing(SubProgramCandidate::interestRate, Comparator.nullsLast(BigDecimal::compareTo))
                .thenComparing(SubProgramCandidate::id));

        if (eligible.isEmpty()) {
            throw new RuntimeException("No eligible program found for invoice discounting");
        }

        SubProgramCandidate chosen = eligible.get(0);
        loan.setProgramId(chosen.programId());
        loan.setSubProgramId(chosen.id());
        applyResolvedInvoiceDiscountingLoanTerms(loan, chosen, invoice);
        log.info(
                "Resolved invoice discounting terms with pre-set programId invoiceId={} borrowerId={} programId={} subProgramId={}",
                loan.getInvoiceId(),
                loan.getBorrowerId(),
                chosen.programId(),
                chosen.id());
    }

    /**
     * After auto-resolving sub-program for invoice discounting, fills missing tenure and rate from sub-program
     * or invoice due date (tenure only), then validates tenure is usable for persistence.
     */
    private void applyResolvedInvoiceDiscountingLoanTerms(
            Loan loan, SubProgramCandidate chosen, Map<String, Object> invoice) {
        LocalDate today = LocalDate.now();

        if (loan.getInterestRate() == null) {
            BigDecimal rate = chosen.interestRate();
            if (rate != null) {
                loan.setInterestRate(rate);
            } else {
                throw new LendingBusinessException(HttpStatus.BAD_REQUEST, MSG_ID_INVOICE_DISCOUNTING_INTEREST);
            }
        }

        if (loan.getTenureDays() == null || loan.getTenureDays() <= 0) {
            Integer maxTenure = chosen.maxTenureDays();
            Integer tenure = null;
            if (maxTenure != null && maxTenure > 0) {
                tenure = maxTenure;
            } else {
                LocalDate dueDate = InvoiceDiscountingProgramRules.parseLocalDate(invoice.get("dueDate"));
                if (dueDate != null) {
                    long daysLong = ChronoUnit.DAYS.between(today, dueDate);
                    if (daysLong > 0) {
                        tenure = daysLong > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) daysLong;
                    }
                }
            }
            if (tenure != null && tenure > 0) {
                loan.setTenureDays(tenure);
            }
        }

        if (loan.getTenureDays() == null || loan.getTenureDays() <= 0) {
            throw new LendingBusinessException(HttpStatus.BAD_REQUEST, MSG_LOAN_TENURE_NOT_CONFIGURED);
        }
    }

    private record SubProgramCandidate(UUID id, UUID programId, BigDecimal interestRate, Integer maxTenureDays) {}

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchInvoiceJson(UUID invoiceId) {
        HttpEntity<Void> psEntity = new HttpEntity<>(ProgramServiceAuthHeaders.trustedInternalHeaders());
        Map<String, Object> invoice = restTemplate.exchange(
                        "http://program-service/api/v1/invoices/{invoiceId}",
                        HttpMethod.GET,
                        psEntity,
                        Map.class,
                        invoiceId)
                .getBody();
        if (invoice == null || invoice.isEmpty()) {
            throw new RuntimeException("Invoice not found: " + invoiceId);
        }
        return invoice;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listAllSubProgramMaps() {
        HttpEntity<Void> psEntity = new HttpEntity<>(ProgramServiceAuthHeaders.trustedInternalHeaders());
        Map<String, Object> resp = restTemplate.exchange(
                        "http://program-service/api/v1/sub-programs",
                        HttpMethod.GET,
                        psEntity,
                        Map.class)
                .getBody();
        if (resp == null || !"SUCCESS".equals(resp.get("status"))) {
            return List.of();
        }
        Object data = resp.get("data");
        if (!(data instanceof List<?> rawList)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : rawList) {
            if (o instanceof Map<?, ?> m) {
                out.add((Map<String, Object>) m);
            }
        }
        return out;
    }

    private boolean isBorrowerEnrolledInSubProgram(UUID subProgramId, UUID borrowerId) {
        try {
            HttpEntity<Void> psEntity = new HttpEntity<>(ProgramServiceAuthHeaders.trustedInternalHeaders());
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.exchange(
                            "http://program-service/api/v1/sub-programs/{subProgramId}/borrowers",
                            HttpMethod.GET,
                            psEntity,
                            Map.class,
                            subProgramId)
                    .getBody();
            if (resp == null || !"SUCCESS".equals(resp.get("status"))) {
                return false;
            }
            Object data = resp.get("data");
            if (!(data instanceof List<?> list)) {
                return false;
            }
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> row)) {
                    continue;
                }
                UUID bid = parseUuid(row.get("borrowerId"));
                if (borrowerId.equals(bid)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.warn("Failed to fetch borrowers for sub-program {}: {}", subProgramId, e.getMessage());
            return false;
        }
    }

    private static UUID parseUuid(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof UUID u) {
            return u;
        }
        String s = raw.toString().trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static BigDecimal parseBigDecimal(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof BigDecimal b) {
            return b;
        }
        if (raw instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(raw.toString());
        } catch (Exception e) {
            return null;
        }
    }

    /** Positive integers only; used for sub-program {@code maxTenureDays}. */
    private static Integer parsePositiveInteger(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            int v = n.intValue();
            return v > 0 ? v : null;
        }
        String s = raw.toString().trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            int v = Integer.parseInt(s);
            return v > 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Resolves {@code programId} from program-service borrower record (Pay Day Loan UI may omit program in payload).
     */
    private UUID fetchProgramIdForBorrower(UUID borrowerId) {
        try {
            HttpEntity<Void> psEntity = new HttpEntity<>(ProgramServiceAuthHeaders.trustedInternalHeaders());
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.exchange(
                            "http://program-service/api/v1/borrowers/{borrowerId}",
                            HttpMethod.GET,
                            psEntity,
                            Map.class,
                            borrowerId)
                    .getBody();
            if (response == null || !"SUCCESS".equals(response.get("status"))) {
                return null;
            }
            Object dataObj = response.get("data");
            if (!(dataObj instanceof Map<?, ?> dataMap)) {
                return null;
            }
            Object programIdObj = dataMap.get("programId");
            if (programIdObj == null) {
                return null;
            }
            return UUID.fromString(programIdObj.toString());
        } catch (Exception e) {
            log.warn("Failed to fetch borrower {} from program-service: {}", borrowerId, e.getMessage());
            return null;
        }
    }

    /**
     * Validates borrower-owned slip and program match; slip must be free for a new disburseable journey.
     */
    private void assertPayDaySalarySlipSelectable(UUID borrowerId, UUID programId, UUID salaryDataId) {
        Map<String, Object> row = salarySlipClient
                .getSalarySlip(salaryDataId)
                .orElseThrow(() -> new LendingBusinessException(HttpStatus.BAD_REQUEST, "Salary slip not found"));
        UUID b = uuidFromRow(row.get("borrowerId"));
        UUID p = uuidFromRow(row.get("programId"));
        if (b == null || !b.equals(borrowerId)) {
            throw new LendingBusinessException(HttpStatus.BAD_REQUEST, "Salary slip does not belong to this borrower");
        }
        if (p == null || !p.equals(programId)) {
            throw new LendingBusinessException(HttpStatus.BAD_REQUEST, "Salary slip does not belong to this program");
        }
        Object stObj = row.get("slipStatus");
        if (stObj == null) {
            return;
        }
        String st = stObj.toString();
        if ("AVAILABLE_FOR_LOAN".equals(st) || SLIP_REJECTED_AGAIN.equals(st)) {
            return;
        }
        throw new LendingBusinessException(HttpStatus.BAD_REQUEST, "Selected salary slip is not available for a new loan");
    }

    private static UUID uuidFromRow(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Primary salary slip row for Pay Day: list all rows for borrower from program-service, pick the most recently
     * updated (then created). Matches eligibility resolution — no DB/schema change.
     */
    private UUID fetchPrimarySalaryDataId(UUID borrowerId) {
        Map<String, Object> chosen = fetchPrimarySalaryRowMap(borrowerId);
        if (chosen == null) {
            return null;
        }
        Object idObj = chosen.get("id");
        if (idObj == null) {
            return null;
        }
        try {
            return UUID.fromString(idObj.toString());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid salary row id for borrower {}: {}", borrowerId, idObj);
            return null;
        }
    }

    /**
     * Returns the salary JSON row map for the borrower's current slip (latest by {@code updatedAt}, then {@code createdAt}).
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchPrimarySalaryRowMap(UUID borrowerId) {
        try {
            HttpEntity<Void> psEntity = new HttpEntity<>(ProgramServiceAuthHeaders.trustedInternalHeaders());
            Map<String, Object> salaryResponse =
                    restTemplate.exchange(
                                    "http://program-service/api/v1/salary?borrowerId={borrowerId}",
                                    HttpMethod.GET,
                                    psEntity,
                                    Map.class,
                                    borrowerId)
                            .getBody();
            if (salaryResponse == null || !"SUCCESS".equals(salaryResponse.get("status"))) {
                return null;
            }
            Object dataObj = salaryResponse.get("data");
            if (!(dataObj instanceof List<?> rawList) || rawList.isEmpty()) {
                return null;
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Object o : rawList) {
                if (o instanceof Map<?, ?> m) {
                    rows.add((Map<String, Object>) m);
                }
            }
            if (rows.isEmpty()) {
                return null;
            }
            return rows.stream()
                    .max(LoanService::compareSalaryRowsByRecency)
                    .orElse(null);
        } catch (Exception e) {
            log.warn("Failed to list salary rows for borrower {}: {}", borrowerId, e.getMessage());
            return null;
        }
    }

    private static int compareSalaryRowsByRecency(Map<String, Object> a, Map<String, Object> b) {
        Instant ua = parseSalaryInstant(a.get("updatedAt"));
        Instant ub = parseSalaryInstant(b.get("updatedAt"));
        int c = ua.compareTo(ub);
        if (c != 0) {
            return c;
        }
        return parseSalaryInstant(a.get("createdAt")).compareTo(parseSalaryInstant(b.get("createdAt")));
    }

    private static Instant parseSalaryInstant(Object raw) {
        if (raw == null) {
            return Instant.EPOCH;
        }
        if (raw instanceof Instant i) {
            return i;
        }
        String s = raw.toString().trim();
        if (s.isEmpty()) {
            return Instant.EPOCH;
        }
        try {
            return Instant.parse(s);
        } catch (Exception e) {
            return Instant.EPOCH;
        }
    }

    private String generateLoanNumber(String productType) {
        String prefix = "PDL".equals(productType) || "PAY_DAY_LOAN".equals(productType) ? "PDL" : "IDF";
        Long seq = (Long) entityManager.createNativeQuery("SELECT nextval('plp_lending.loan_number_seq')")
                .getSingleResult();
        return prefix + "-" + seq;
    }

    private static BigDecimal extractPendingDisbursementAmount(Loan loan) {
        Map<String, Object> snap = loan.getEligibilitySnapshot();
        if (snap == null) {
            return null;
        }
        Object raw = snap.get("pendingDisbursementAmount");
        if (raw == null) {
            return null;
        }
        return new BigDecimal(raw.toString());
    }

    private static void clearPendingDisbursementSnapshot(Loan loan) {
        Map<String, Object> snap = loan.getEligibilitySnapshot();
        if (snap == null) {
            return;
        }
        Map<String, Object> copy = new HashMap<>(snap);
        copy.remove("pendingDisbursementAmount");
        loan.setEligibilitySnapshot(copy.isEmpty() ? null : copy);
    }

    /** Program-service: clear FINANCING_REQUESTED after cancelling pending disbursement (invoice-discounting only). */
    private void revertInvoiceFinancingRequested(UUID invoiceId) {
        try {
            HttpEntity<Void> entity = new HttpEntity<>(ProgramServiceAuthHeaders.trustedInternalHeaders());
            ResponseEntity<Void> response =
                    restTemplate.exchange(
                            "http://program-service/api/v1/invoices/{invoiceId}/cancel-financing-requested",
                            HttpMethod.POST,
                            entity,
                            Void.class,
                            invoiceId);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new LendingBusinessException(
                        HttpStatus.BAD_GATEWAY,
                        "Unable to revert invoice financing state: HTTP " + response.getStatusCode().value());
            }
            log.info(
                    "program-service cancel-financing-requested completed for invoice {} (HTTP {})",
                    invoiceId,
                    response.getStatusCode().value());
        } catch (RestClientResponseException e) {
            log.error(
                    "cancel-financing-requested REST error for invoice {}: HTTP {} body={}",
                    invoiceId,
                    e.getStatusCode().value(),
                    e.getResponseBodyAsString());
            throw RestClientIntegrationMapper.toBusinessException(e, "Unable to revert invoice financing state");
        } catch (LendingBusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed cancel-financing-requested for invoice {}: {}", invoiceId, e.getMessage());
            throw new LendingBusinessException(
                    HttpStatus.BAD_GATEWAY, "Unable to revert invoice financing state: " + e.getMessage());
        }
    }

    private void markInvoiceFinancingRequested(UUID invoiceId) {
        log.info("mark-financing-requested: invoking program-service for invoiceId={}", invoiceId);
        try {
            HttpEntity<Void> entity = new HttpEntity<>(ProgramServiceAuthHeaders.trustedInternalHeaders());
            ResponseEntity<Void> response =
                    restTemplate.exchange(
                            "http://program-service/api/v1/invoices/{invoiceId}/mark-financing-requested",
                            HttpMethod.POST,
                            entity,
                            Void.class,
                            invoiceId);
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error(
                        "mark-financing-requested failed for invoiceId={}: HTTP {} (expected 2xx)",
                        invoiceId,
                        response.getStatusCode().value());
                throw new RuntimeException(
                        "mark-financing-requested failed with HTTP " + response.getStatusCode().value());
            }
            log.info(
                    "mark-financing-requested succeeded for invoiceId={} (HTTP {})",
                    invoiceId,
                    response.getStatusCode().value());
        } catch (RestClientResponseException e) {
            int code = e.getStatusCode().value();
            log.error(
                    "mark-financing-requested REST failure for invoiceId={}: HTTP {} body={}",
                    invoiceId,
                    code,
                    e.getResponseBodyAsString());
            if (code == 409) {
                throw new LendingBusinessException(
                        HttpStatus.CONFLICT, RestClientIntegrationMapper.duplicateInvoiceFinancingMessage());
            }
            throw RestClientIntegrationMapper.toBusinessException(e, "Unable to update invoice after financing request");
        } catch (LendingBusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error(
                    "mark-financing-requested unexpected failure for invoiceId={}: {}",
                    invoiceId,
                    e.getMessage(),
                    e);
            throw new RuntimeException("Unable to update invoice after financing request: " + e.getMessage(), e);
        }
    }

    private void validateInvoiceEligibleForFinancing(
            UUID invoiceId,
            UUID borrowerId,
            UUID programId,
            BigDecimal requestedAmount) {
        HttpEntity<Void> psEntity = new HttpEntity<>(ProgramServiceAuthHeaders.trustedInternalHeaders());
        @SuppressWarnings("unchecked")
        Map<String, Object> invoiceResponse = restTemplate.exchange(
                        "http://program-service/api/v1/invoices/{invoiceId}",
                        HttpMethod.GET,
                        psEntity,
                        Map.class,
                        invoiceId)
                .getBody();
        if (invoiceResponse == null) {
            throw new RuntimeException("Invoice not eligible for financing");
        }
        invoiceSubProgramValidator
                .validateInvoiceSubProgramLink(invoiceResponse, borrowerId, programId)
                .ifPresent(msg -> {
                    throw new RuntimeException(msg);
                });
        subProgramLimits
                .validateInvoiceSubProgramLimits(invoiceResponse, borrowerId, requestedAmount)
                .ifPresent(msg -> {
                    throw new RuntimeException(msg);
                });

        Object invoiceProgramIdObj = invoiceResponse.get("programId");
        if (invoiceProgramIdObj != null) {
            try {
                UUID invoiceProgramId = UUID.fromString(invoiceProgramIdObj.toString());
                Map<String, Object> programConfig =
                        programServiceProgramConfigClient.fetchProgramConfig(invoiceProgramId);
                List<String> configFailures = InvoiceDiscountingProgramRules.evaluate(
                        LocalDate.now(),
                        InvoiceDiscountingProgramRules.parseLocalDate(invoiceResponse.get("invoiceDate")),
                        InvoiceDiscountingProgramRules.parseLocalDate(invoiceResponse.get("dueDate")),
                        InvoiceDiscountingProgramRules.parseInvoiceAmount(invoiceResponse.get("invoiceAmount")),
                        programConfig);
                if (!configFailures.isEmpty()) {
                    throw new RuntimeException(String.join("; ", configFailures));
                }
            } catch (IllegalArgumentException ignored) {
                // skip program-config rules when program id invalid
            }
        }

        Object statusObj = invoiceResponse.get("status");
        String status = statusObj != null ? statusObj.toString() : "";

        if (INVOICE_STATUS_FINANCING_REQUESTED.equals(status)) {
            throw new LendingBusinessException(
                    HttpStatus.CONFLICT, RestClientIntegrationMapper.duplicateInvoiceFinancingMessage());
        }

        Object flowObj = invoiceResponse.get("flowType");
        String flow = flowObj != null ? flowObj.toString().trim() : "";
        boolean purchaseFlow = flow.isEmpty() || FLOW_PURCHASE_BILL_DISCOUNTING.equals(flow);
        boolean salesFlow = FLOW_SALES_BILL_DISCOUNTING.equals(flow);

        boolean ok;
        if (purchaseFlow) {
            ok = "BORROWER_ACCEPTED".equals(status) || "PARTIALLY_DISCOUNTED".equals(status);
        } else if (salesFlow) {
            ok = "ELIGIBLE".equals(status) || "PARTIALLY_DISCOUNTED".equals(status);
        } else {
            ok = false;
        }
        if (!ok) {
            throw new RuntimeException("Invoice not eligible for financing");
        }
    }
}
