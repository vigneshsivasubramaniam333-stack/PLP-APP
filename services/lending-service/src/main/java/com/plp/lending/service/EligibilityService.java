package com.plp.lending.service;

import com.plp.lending.integration.ProgramServiceAuthHeaders;
import com.plp.lending.integration.ProgramServiceInvoiceSubProgramValidator;
import com.plp.lending.integration.ProgramServiceProgramConfigClient;
import com.plp.lending.integration.ProgramServiceSalarySlipClient;
import com.plp.lending.integration.ProgramServiceSubProgramLimits;
import com.plp.lending.model.entity.Loan;
import com.plp.lending.model.enums.LoanStatus;
import com.plp.lending.repository.LoanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EligibilityService {

    private static final String FLOW_PURCHASE_BILL_DISCOUNTING = "PURCHASE_BILL_DISCOUNTING";
    private static final String FLOW_SALES_BILL_DISCOUNTING = "SALES_BILL_DISCOUNTING";

    private static final String FLOW_PAY_LOAN = "PAY_LOAN";
    private static final String FLOW_PAY_DAY_LOAN = "PAY_DAY_LOAN";

    private static final String MSG_PAY_LOAN_NO_QUALIFYING_LINK =
            "No active Pay Loan sub-program found for borrower";
    private static final String MSG_PAY_DAY_LIMIT_INSUFFICIENT =
            "Available limit is insufficient for this loan amount";

    private static final String MSG_PAY_DAY_LOAN_EXISTS_FOR_SALARY_PERIOD =
            "Loan already exists for this salary period.";

    /**
     * Same salary slip cannot back a second Pay Loan while any of these loan rows exist for that {@code salaryDataId}.
     */
    private static final List<LoanStatus> PAY_DAY_LINKED_ACTIVE_LOAN_STATUSES = List.of(
            LoanStatus.REQUESTED,
            LoanStatus.ELIGIBILITY_CHECK,
            LoanStatus.SANCTIONED,
            LoanStatus.DISBURSEMENT_PENDING,
            LoanStatus.DISBURSED,
            LoanStatus.REPAYMENT_DUE,
            LoanStatus.OVERDUE,
            LoanStatus.CLOSED);

    /** In-flight Pay Day loans on the same program that count toward {@code maxConcurrentLoans}. */
    private static final List<LoanStatus> PAY_DAY_CONCURRENT_LOAN_STATUSES = List.of(
            LoanStatus.REQUESTED,
            LoanStatus.ELIGIBILITY_CHECK,
            LoanStatus.SANCTIONED,
            LoanStatus.DISBURSEMENT_PENDING,
            LoanStatus.DISBURSED,
            LoanStatus.REPAYMENT_DUE,
            LoanStatus.OVERDUE);

    private static final String DERIVED_AVAILABLE_FOR_LOAN = "AVAILABLE_FOR_LOAN";
    private static final String DERIVED_LOAN_REQUESTED = "LOAN_REQUESTED";
    private static final String DERIVED_REJECTED_AVAILABLE_AGAIN = "REJECTED_AVAILABLE_AGAIN";
    private static final String DERIVED_USED_FOR_LOAN = "USED_FOR_LOAN";

    private static final Comparator<PayLoanLimitCandidate> PAY_LOAN_LIMIT_COMPARATOR =
            Comparator.comparing(PayLoanLimitCandidate::interestRate, Comparator.nullsLast(BigDecimal::compareTo))
                    .thenComparing(PayLoanLimitCandidate::subProgramId);

    private record PayLoanLimitCandidate(
            UUID subProgramId,
            BigDecimal interestRate,
            BigDecimal effectiveLimit,
            BigDecimal subProgramAvailable,
            BigDecimal borrowerAvailable) {}

    private final LoanRepository loanRepository;
    private final RestTemplate restTemplate;
    private final ProgramServiceSalarySlipClient salarySlipClient;
    private final ProgramServiceInvoiceSubProgramValidator invoiceSubProgramValidator;
    private final ProgramServiceSubProgramLimits subProgramLimits;
    private final ProgramServiceProgramConfigClient programServiceProgramConfigClient;

    private static final String SLIP_AVAILABLE = "AVAILABLE_FOR_LOAN";
    private static final String SLIP_REJECTED_AGAIN = "REJECTED_AVAILABLE_AGAIN";
    private static final String SLIP_LOAN_REQUESTED = "LOAN_REQUESTED";
    private static final String SLIP_DISBURSED_USED = "DISBURSED_USED";
    private static final String SLIP_CLOSED_USED = "CLOSED_USED";

    /**
     * Check eligibility for a Pay Day Loan request.
     * Returns eligibility result with max eligible amount and reasons.
     */
    public Map<String, Object> checkPayDayLoanEligibility(UUID borrowerId, UUID programId, BigDecimal requestedAmount) {
        return checkPayDayLoanEligibility(borrowerId, programId, requestedAmount, null);
    }

    /**
     * @param selectedSalaryDataId when set, eligibility is evaluated only for that slip (no silent "latest" override).
     */
    public Map<String, Object> checkPayDayLoanEligibility(
            UUID borrowerId, UUID programId, BigDecimal requestedAmount, UUID selectedSalaryDataId) {
        Map<String, Object> result = new HashMap<>();
        result.put("borrowerId", borrowerId.toString());
        result.put("programId", programId.toString());
        result.put("requestedAmount", requestedAmount);

        List<String> reasons = new java.util.ArrayList<>();
        boolean eligible = true;
        PayLoanLimitCandidate payDayLimitDebugCandidate = null;

        // 1. Overdue (any product)
        boolean hasOverdue =
                loanRepository.findByBorrowerId(borrowerId).stream().anyMatch(l -> l.getStatus() == LoanStatus.OVERDUE);
        if (hasOverdue) {
            eligible = false;
            reasons.add("Borrower has overdue loans");
        }

        // 2. Concurrent Pay Day loans on this program vs maxConcurrentLoans (program borrower_limits)
        int maxConcurrentLoans = 1;
        try {
            HttpEntity<Void> psEntity = new HttpEntity<>(ProgramServiceAuthHeaders.trustedInternalHeaders());
            @SuppressWarnings("unchecked")
            Map<String, Object> limitResponse = restTemplate.exchange(
                            "http://program-service/api/v1/borrowers/{borrowerId}/limits?programId={programId}",
                            HttpMethod.GET,
                            psEntity,
                            Map.class,
                            borrowerId,
                            programId)
                    .getBody();
            if (limitResponse != null && "SUCCESS".equals(limitResponse.get("status"))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> limitData = (Map<String, Object>) limitResponse.get("data");
                if (limitData != null && limitData.containsKey("maxConcurrentLoans")) {
                    maxConcurrentLoans = ((Number) limitData.get("maxConcurrentLoans")).intValue();
                }
            }
        } catch (Exception e) {
            log.debug("Optional maxConcurrentLoans fetch skipped for borrower {}: {}", borrowerId, e.getMessage());
        }

        int openPayDayLoansOnProgram =
                (int)
                        loanRepository.findByBorrowerId(borrowerId).stream()
                                .filter(l -> programId.equals(l.getProgramId()))
                                .filter(l -> "PAY_DAY_LOAN".equals(l.getProductType()))
                                .filter(l -> PAY_DAY_CONCURRENT_LOAN_STATUSES.contains(l.getStatus()))
                                .count();

        // 2c. Pay Day Loan: borrower limit from active PAY_LOAN / PAY_DAY_LOAN sub-program membership (sub_program_borrowers)
        try {
            List<Map<String, Object>> programSubPrograms = fetchProgramSubPrograms(programId);
            List<PayLoanLimitCandidate> enrolled = new ArrayList<>();
            for (Map<String, Object> sp : programSubPrograms) {
                if (!isActivePayLoanSubProgram(sp)) {
                    continue;
                }
                UUID spId = parseUuid(sp.get("id"));
                if (spId == null) {
                    continue;
                }
                Optional<ProgramServiceSubProgramLimits.DualSubProgramBorrowerAvailability> dual =
                        subProgramLimits.fetchDualLimitHeadroom(spId, borrowerId);
                if (dual.isEmpty()) {
                    continue;
                }
                ProgramServiceSubProgramLimits.DualSubProgramBorrowerAvailability d = dual.get();
                enrolled.add(new PayLoanLimitCandidate(
                        spId,
                        parseBigDecimal(sp.get("interestRate")),
                        d.effectiveAvailable(),
                        d.subProgramAvailable(),
                        d.borrowerAvailable()));
            }

            boolean anyPayLoanEnrollment = !enrolled.isEmpty();
            List<PayLoanLimitCandidate> sufficient =
                    enrolled.stream()
                            .filter(c -> c.effectiveLimit().compareTo(requestedAmount) >= 0)
                            .sorted(PAY_LOAN_LIMIT_COMPARATOR)
                            .toList();

            if (!enrolled.isEmpty()) {
                payDayLimitDebugCandidate =
                        sufficient.isEmpty()
                                ? enrolled.stream().min(PAY_LOAN_LIMIT_COMPARATOR).orElse(null)
                                : sufficient.get(0);
            }

            if (sufficient.isEmpty()) {
                eligible = false;
                if (!anyPayLoanEnrollment) {
                    reasons.add(MSG_PAY_LOAN_NO_QUALIFYING_LINK);
                } else {
                    reasons.add(MSG_PAY_DAY_LIMIT_INSUFFICIENT);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to resolve Pay Loan sub-program limits for borrower {}: {}", borrowerId, e.getMessage());
            eligible = false;
            reasons.add(MSG_PAY_LOAN_NO_QUALIFYING_LINK);
        }

        if (openPayDayLoansOnProgram >= maxConcurrentLoans) {
            eligible = false;
            reasons.add("Maximum concurrent loans reached: " + openPayDayLoansOnProgram + "/" + maxConcurrentLoans);
        }

        // 3. Salary slip: explicit selection takes precedence; otherwise pick best eligible slip for this program.
        BigDecimal eligibleAmount = BigDecimal.ZERO;
        UUID latestSalaryDataId = null;
        Map<String, Object> primarySalaryRow = null;
        String persistedSlipStatus = null;
        try {
            if (selectedSalaryDataId != null) {
                primarySalaryRow =
                        salarySlipClient.getSalarySlip(selectedSalaryDataId).orElse(null);
                if (primarySalaryRow == null) {
                    eligible = false;
                    reasons.add("Selected salary slip was not found");
                } else {
                    UUID slipBorrower = parseUuid(primarySalaryRow.get("borrowerId"));
                    UUID slipProgram = parseUuid(primarySalaryRow.get("programId"));
                    if (slipBorrower == null || !borrowerId.equals(slipBorrower)) {
                        eligible = false;
                        reasons.add("Salary slip does not belong to this borrower");
                    }
                    if (slipProgram == null || !programId.equals(slipProgram)) {
                        eligible = false;
                        reasons.add("Salary slip does not belong to this program");
                    }
                    if (primarySalaryRow.get("slipStatus") != null) {
                        persistedSlipStatus = primarySalaryRow.get("slipStatus").toString();
                        if (SLIP_DISBURSED_USED.equals(persistedSlipStatus)
                                || SLIP_CLOSED_USED.equals(persistedSlipStatus)) {
                            eligible = false;
                            reasons.add("This salary slip has already been used for a loan and cannot be reused");
                        } else if (SLIP_LOAN_REQUESTED.equals(persistedSlipStatus)) {
                            eligible = false;
                            reasons.add("A loan request is already in progress for this salary slip");
                        }
                    }
                    Object idRaw = primarySalaryRow.get("id");
                    if (idRaw != null) {
                        try {
                            latestSalaryDataId = UUID.fromString(idRaw.toString());
                        } catch (IllegalArgumentException ignored) {
                            latestSalaryDataId = null;
                        }
                    }
                    if (primarySalaryRow.containsKey("eligibleAmount")) {
                        Object amt = primarySalaryRow.get("eligibleAmount");
                        eligibleAmount = amt instanceof Number
                                ? BigDecimal.valueOf(((Number) amt).doubleValue())
                                : new BigDecimal(amt.toString());
                    }
                }
            } else {
                primarySalaryRow = resolveDefaultEligibleSalaryRow(borrowerId, programId);
                if (primarySalaryRow == null) {
                    primarySalaryRow = fetchPrimarySalaryRowMap(borrowerId);
                }
                if (primarySalaryRow != null) {
                    if (primarySalaryRow.get("slipStatus") != null) {
                        persistedSlipStatus = primarySalaryRow.get("slipStatus").toString();
                    }
                    Object idRaw = primarySalaryRow.get("id");
                    if (idRaw != null) {
                        try {
                            latestSalaryDataId = UUID.fromString(idRaw.toString());
                        } catch (IllegalArgumentException ignored) {
                            latestSalaryDataId = null;
                        }
                    }
                    if (primarySalaryRow.containsKey("eligibleAmount")) {
                        Object amt = primarySalaryRow.get("eligibleAmount");
                        eligibleAmount = amt instanceof Number
                                ? BigDecimal.valueOf(((Number) amt).doubleValue())
                                : new BigDecimal(amt.toString());
                    }
                    if (persistedSlipStatus != null) {
                        if (SLIP_DISBURSED_USED.equals(persistedSlipStatus)
                                || SLIP_CLOSED_USED.equals(persistedSlipStatus)) {
                            eligible = false;
                            reasons.add("No eligible salary slip available for a new loan");
                        } else if (SLIP_LOAN_REQUESTED.equals(persistedSlipStatus)) {
                            eligible = false;
                            reasons.add("A loan request is already in progress for the current salary slip");
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch salary data for borrower {}: {}", borrowerId, e.getMessage());
            eligible = false;
            reasons.add("Salary data not available");
        }

        final UUID slipSalaryId = latestSalaryDataId;
        List<Loan> salarySlipLoans =
                slipSalaryId == null
                        ? List.of()
                        : loanRepository.findByBorrowerId(borrowerId).stream()
                                .filter(l -> "PAY_DAY_LOAN".equals(l.getProductType()))
                                .filter(l -> slipSalaryId.equals(l.getSalaryDataId()))
                                .toList();
        String derivedSalaryStatus =
                resolveDerivedSalaryLabel(persistedSlipStatus, primarySalaryRow, salarySlipLoans);

        if (eligibleAmount.compareTo(BigDecimal.ZERO) <= 0) {
            eligible = false;
            reasons.add("No eligible salary amount for current period");
        }

        if (latestSalaryDataId != null
                && loanRepository.existsBySalaryDataIdAndStatusIn(
                        latestSalaryDataId, PAY_DAY_LINKED_ACTIVE_LOAN_STATUSES)) {
            eligible = false;
            reasons.add(MSG_PAY_DAY_LOAN_EXISTS_FOR_SALARY_PERIOD);
        }

        // 4. Check requested amount against eligible
        if (eligible && requestedAmount.compareTo(eligibleAmount) > 0) {
            eligible = false;
            reasons.add("Requested amount exceeds eligible amount: " + eligibleAmount);
        }

        result.put("eligible", eligible);
        result.put("eligibleAmount", eligibleAmount);
        result.put("activeLoans", openPayDayLoansOnProgram);
        result.put("reasons", reasons);
        result.put("salaryDataId", latestSalaryDataId != null ? latestSalaryDataId.toString() : null);
        result.put("slipStatus", persistedSlipStatus);
        result.put("derivedSalaryStatus", derivedSalaryStatus);
        result.put(
                "subProgramAvailable",
                payDayLimitDebugCandidate != null ? payDayLimitDebugCandidate.subProgramAvailable() : null);
        result.put(
                "borrowerAvailable",
                payDayLimitDebugCandidate != null ? payDayLimitDebugCandidate.borrowerAvailable() : null);

        log.info(
                "Eligibility check: borrower={} program={} eligible={} eligibleAmount={} requested={} salaryDataId={} derivedSalaryStatus={} openPayDayLoans={}/{}",
                borrowerId,
                programId,
                eligible,
                eligibleAmount,
                requestedAmount,
                latestSalaryDataId,
                derivedSalaryStatus,
                openPayDayLoansOnProgram,
                maxConcurrentLoans);
        return result;
    }

    public Map<String, Object> checkInvoiceDiscountingEligibility(UUID borrowerId, UUID programId,
                                                                    UUID invoiceId, BigDecimal requestedAmount) {
        Map<String, Object> result = new HashMap<>();
        result.put("borrowerId", borrowerId.toString());
        result.put("programId", programId.toString());
        result.put("invoiceId", invoiceId.toString());
        result.put("requestedAmount", requestedAmount);

        List<String> reasons = new java.util.ArrayList<>();
        boolean eligible = true;

        // 1. Check for overdue loans
        boolean hasOverdue = loanRepository.findByBorrowerId(borrowerId).stream()
                .anyMatch(l -> l.getStatus() == LoanStatus.OVERDUE);
        if (hasOverdue) {
            eligible = false;
            reasons.add("Borrower has overdue loans");
        }

        // 2. Fetch invoice data from program-service
        BigDecimal availableAmount = BigDecimal.ZERO;
        String invoiceStatus = "UNKNOWN";
        String invoiceFlowType = "";
        boolean invoiceDataLoaded = false;
        try {
            HttpEntity<Void> psEntity = new HttpEntity<>(ProgramServiceAuthHeaders.trustedInternalHeaders());
            @SuppressWarnings("unchecked")
            Map<String, Object> invoiceResponse = restTemplate.exchange(
                            "http://program-service/api/v1/invoices/{invoiceId}",
                            HttpMethod.GET,
                            psEntity,
                            Map.class,
                            invoiceId)
                    .getBody();
            if (invoiceResponse != null) {
                invoiceDataLoaded = true;
                Object avail = invoiceResponse.get("availableAmount");
                if (avail != null) {
                    availableAmount = avail instanceof Number
                            ? BigDecimal.valueOf(((Number) avail).doubleValue())
                            : new BigDecimal(avail.toString());
                }
                Object status = invoiceResponse.get("status");
                if (status != null) {
                    invoiceStatus = status.toString();
                }
                Object flowTypeObj = invoiceResponse.get("flowType");
                invoiceFlowType = flowTypeObj != null ? flowTypeObj.toString().trim() : "";

                Object verified = invoiceResponse.get("verified");
                if (verified == null || !Boolean.TRUE.equals(verified)) {
                    eligible = false;
                    reasons.add("Invoice is not verified");
                }

                Optional<String> subProgramIssue = invoiceSubProgramValidator
                        .validateInvoiceSubProgramLink(invoiceResponse, borrowerId, programId);
                if (subProgramIssue.isPresent()) {
                    eligible = false;
                    reasons.add(subProgramIssue.get());
                }

                Optional<String> subProgramLimitIssue = subProgramLimits.validateInvoiceSubProgramLimits(
                        invoiceResponse, borrowerId, requestedAmount);
                if (subProgramLimitIssue.isPresent()) {
                    eligible = false;
                    reasons.add(subProgramLimitIssue.get());
                }

                Object programIdObj = invoiceResponse.get("programId");
                if (programIdObj != null) {
                    try {
                        UUID invoiceProgramId = UUID.fromString(programIdObj.toString());
                        Map<String, Object> programConfig =
                                programServiceProgramConfigClient.fetchProgramConfig(invoiceProgramId);
                        List<String> configReasons = InvoiceDiscountingProgramRules.evaluate(
                                LocalDate.now(),
                                InvoiceDiscountingProgramRules.parseLocalDate(invoiceResponse.get("invoiceDate")),
                                InvoiceDiscountingProgramRules.parseLocalDate(invoiceResponse.get("dueDate")),
                                InvoiceDiscountingProgramRules.parseInvoiceAmount(invoiceResponse.get("invoiceAmount")),
                                programConfig);
                        for (String r : configReasons) {
                            eligible = false;
                            reasons.add(r);
                        }
                    } catch (IllegalArgumentException ignored) {
                        // invalid program id on invoice — skip config rules
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch invoice data for invoice {}: {}", invoiceId, e.getMessage());
            eligible = false;
            reasons.add("Invoice data not available");
        }

        result.put("flowType", invoiceFlowType.isEmpty() ? FLOW_PURCHASE_BILL_DISCOUNTING : invoiceFlowType);

        // 3. Flow-specific: purchase bill requires borrower acceptance; sales bill after anchor ELIGIBLE
        if (invoiceDataLoaded) {
            if ("FINANCING_REQUESTED".equals(invoiceStatus)) {
                eligible = false;
                reasons.add("A financing request is already in progress for this invoice");
            } else {
                boolean purchaseFlow =
                        invoiceFlowType.isEmpty() || FLOW_PURCHASE_BILL_DISCOUNTING.equals(invoiceFlowType);
                boolean salesFlow = FLOW_SALES_BILL_DISCOUNTING.equals(invoiceFlowType);
                boolean statusOk = false;
                if (purchaseFlow) {
                    statusOk = "BORROWER_ACCEPTED".equals(invoiceStatus) || "PARTIALLY_DISCOUNTED".equals(invoiceStatus);
                    if (!statusOk) {
                        eligible = false;
                        if ("ELIGIBLE".equals(invoiceStatus)) {
                            reasons.add("Borrower must accept this invoice before requesting discounting");
                        } else {
                            reasons.add("Invoice status not eligible for financing: " + invoiceStatus);
                        }
                    }
                } else if (salesFlow) {
                    statusOk = "ELIGIBLE".equals(invoiceStatus) || "PARTIALLY_DISCOUNTED".equals(invoiceStatus);
                    if (!statusOk) {
                        eligible = false;
                        reasons.add("Invoice status not eligible for financing: " + invoiceStatus);
                    }
                } else {
                    eligible = false;
                    reasons.add("Unknown or unsupported invoice flowType: " + invoiceFlowType);
                }
            }
        }

        // 4. Check available amount on invoice
        if (availableAmount.compareTo(BigDecimal.ZERO) <= 0) {
            eligible = false;
            reasons.add("No available amount on this invoice");
        }

        // 5. Check requested amount
        if (eligible && requestedAmount.compareTo(availableAmount) > 0) {
            eligible = false;
            reasons.add("Requested amount exceeds available: " + availableAmount);
        }

        // 6. Check borrower limit via program-service
        try {
            HttpEntity<Void> psEntity = new HttpEntity<>(ProgramServiceAuthHeaders.trustedInternalHeaders());
            @SuppressWarnings("unchecked")
            Map<String, Object> limitResponse = restTemplate.exchange(
                            "http://program-service/api/v1/borrowers/{borrowerId}/limits?programId={programId}",
                            HttpMethod.GET,
                            psEntity,
                            Map.class,
                            borrowerId,
                            programId)
                    .getBody();
            if (limitResponse != null && "SUCCESS".equals(limitResponse.get("status"))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> limitData = (Map<String, Object>) limitResponse.get("data");
                if (limitData != null) {
                    Object availLimit = limitData.get("availableLimit");
                    if (availLimit != null) {
                        BigDecimal limit = availLimit instanceof Number
                                ? BigDecimal.valueOf(((Number) availLimit).doubleValue())
                                : new BigDecimal(availLimit.toString());
                        if (eligible && requestedAmount.compareTo(limit) > 0) {
                            eligible = false;
                            reasons.add("Requested amount exceeds borrower limit: " + limit);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch limit for borrower {}: {}", borrowerId, e.getMessage());
            eligible = false;
            reasons.add("Borrower limit data not available");
        }

        result.put("eligible", eligible);
        result.put("availableAmount", availableAmount);
        result.put("invoiceStatus", invoiceStatus);
        result.put("reasons", reasons);

        log.info("Invoice eligibility: borrower={} invoice={} eligible={} available={} requested={}",
                borrowerId, invoiceId, eligible, availableAmount, requestedAmount);
        return result;
    }

    private List<Map<String, Object>> fetchProgramSubPrograms(UUID programId) {
        HttpEntity<Void> psEntity = new HttpEntity<>(ProgramServiceAuthHeaders.trustedInternalHeaders());
        @SuppressWarnings("unchecked")
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
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Same row resolution as {@link LoanService}: list salary API, then choose max(updatedAt, then createdAt).
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
            return rows.stream().max(EligibilityService::compareSalaryRowsByRecency).orElse(null);
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

    /**
     * Derived lifecycle label for the primary salary slip (no DB): consumed → in-flight → rejected-only → free.
     */
    private static String derivePayDaySalarySlipStatus(List<Loan> slipLoans) {
        if (slipLoans.isEmpty()) {
            return DERIVED_AVAILABLE_FOR_LOAN;
        }
        boolean consumed =
                slipLoans.stream()
                        .anyMatch(
                                l ->
                                        l.getStatus() == LoanStatus.DISBURSED
                                                || l.getStatus() == LoanStatus.REPAYMENT_DUE
                                                || l.getStatus() == LoanStatus.OVERDUE
                                                || l.getStatus() == LoanStatus.CLOSED
                                                || l.getStatus() == LoanStatus.WRITTEN_OFF);
        if (consumed) {
            return DERIVED_USED_FOR_LOAN;
        }
        boolean inFlight =
                slipLoans.stream()
                        .anyMatch(
                                l ->
                                        l.getStatus() == LoanStatus.REQUESTED
                                                || l.getStatus() == LoanStatus.ELIGIBILITY_CHECK
                                                || l.getStatus() == LoanStatus.SANCTIONED
                                                || l.getStatus() == LoanStatus.DISBURSEMENT_PENDING);
        if (inFlight) {
            return DERIVED_LOAN_REQUESTED;
        }
        boolean rejectedOnly =
                slipLoans.stream()
                        .allMatch(
                                l ->
                                        l.getStatus() == LoanStatus.REJECTED
                                                || l.getStatus() == LoanStatus.CANCELLED);
        if (rejectedOnly) {
            return DERIVED_REJECTED_AVAILABLE_AGAIN;
        }
        return DERIVED_AVAILABLE_FOR_LOAN;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchSalaryRowList(UUID borrowerId) {
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
                return List.of();
            }
            Object dataObj = salaryResponse.get("data");
            if (!(dataObj instanceof List<?> rawList)) {
                return List.of();
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Object o : rawList) {
                if (o instanceof Map<?, ?> m) {
                    rows.add((Map<String, Object>) m);
                }
            }
            return rows;
        } catch (Exception e) {
            log.warn("Failed to list salary rows for borrower {}: {}", borrowerId, e.getMessage());
            return List.of();
        }
    }

    private Map<String, Object> resolveDefaultEligibleSalaryRow(UUID borrowerId, UUID programId) {
        List<Map<String, Object>> rows = fetchSalaryRowList(borrowerId);
        List<Map<String, Object>> candidates = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            if (!programId.equals(parseUuid(r.get("programId")))) {
                continue;
            }
            Object st = r.get("slipStatus");
            if (st == null) {
                candidates.add(r);
                continue;
            }
            String s = st.toString();
            if (SLIP_AVAILABLE.equals(s) || SLIP_REJECTED_AGAIN.equals(s)) {
                candidates.add(r);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.stream().max(EligibilityService::compareSalaryRowsByRecency).orElse(null);
    }

    private static String resolveDerivedSalaryLabel(
            String persistedSlipStatus, Map<String, Object> primaryRow, List<Loan> slipLoans) {
        String slip = persistedSlipStatus;
        if (slip == null && primaryRow != null && primaryRow.get("slipStatus") != null) {
            slip = primaryRow.get("slipStatus").toString();
        }
        if (slip != null) {
            if (SLIP_DISBURSED_USED.equals(slip) || SLIP_CLOSED_USED.equals(slip)) {
                return DERIVED_USED_FOR_LOAN;
            }
            if (SLIP_AVAILABLE.equals(slip)) {
                return DERIVED_AVAILABLE_FOR_LOAN;
            }
            if (SLIP_REJECTED_AGAIN.equals(slip)) {
                return DERIVED_REJECTED_AVAILABLE_AGAIN;
            }
            if (SLIP_LOAN_REQUESTED.equals(slip)) {
                return DERIVED_LOAN_REQUESTED;
            }
            return slip;
        }
        return derivePayDaySalarySlipStatus(slipLoans);
    }
}
