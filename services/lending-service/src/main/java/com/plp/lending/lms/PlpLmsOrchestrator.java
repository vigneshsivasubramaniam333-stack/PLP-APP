package com.plp.lending.lms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plp.encore.client.api.EncoreLmsApi;
import com.plp.encore.client.api.EncoreOpenLoanParams;
import com.plp.lending.model.entity.LmsLoanOperation;
import com.plp.lending.model.entity.Loan;
import com.plp.lending.repository.LmsLoanOperationRepository;
import com.plp.lending.repository.LoanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Optional Encore LMS hooks for PLP loans (bl-core {@code lms_entry_in} parity). Non-blocking: failures are logged only.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlpLmsOrchestrator {

    private static final String OP_OPEN = "OPEN";
    private static final String OP_DISBURSE = "DISBURSE";
    private static final String OP_REPAY = "REPAY";
    private static final String OP_SUMMARY = "SUMMARY";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_ERROR = "ERROR";

    private final EncoreLmsApi encoreLmsApi;
    private final PlpEncoreLmsAdapter plpEncoreLmsAdapter;
    private final ProgramLmsConfigClient programLmsConfigClient;
    private final LmsLoanOperationRepository lmsLoanOperationRepository;
    private final LoanRepository loanRepository;
    private final ObjectMapper objectMapper;

    public void onLoanSanctioned(Loan loan) {
        ProgramLmsConfig cfg = programLmsConfigClient.fetch(loan.getProgramId());
        if (!cfg.isLmsEnabled()) {
            log.info("PLP LMS skipped for {} — program lms_entry_in is not YES (programId={})",
                    loan.getLoanNumber(), loan.getProgramId());
            return;
        }
        if (!encoreLmsApi.isActive()) {
            log.info("PLP LMS open skipped — Encore not configured for loan {}", loan.getLoanNumber());
            return;
        }
        if (loan.getLmsAccountId() != null && !loan.getLmsAccountId().isBlank()) {
            log.info("PLP LMS open skipped — account already exists for {}", loan.getLoanNumber());
            return;
        }
        try {
            Map<String, Object> borrower = programLmsConfigClient.fetchBorrower(loan.getBorrowerId());
            String borrowerName = stringVal(borrower.get("name"));
            if (borrowerName == null || borrowerName.isBlank()) {
                borrowerName = loan.getLoanNumber();
            }
            BigDecimal amount = loan.getSanctionedAmount() != null ? loan.getSanctionedAmount() : loan.getRequestedAmount();
            BigDecimal rate = loan.getInterestRate() != null ? loan.getInterestRate() : BigDecimal.ZERO;
            int tenureDays = loan.getTenureDays() != null ? loan.getTenureDays() : 1;

            String productCode = cfg.getEncoreProductCode();
            if (productCode == null || productCode.isBlank()) {
                log.warn("PLP LMS open: encore product code missing for program {} loan {}", loan.getProgramId(), loan.getLoanNumber());
                return;
            }

            EncoreOpenLoanParams params;
            if ("INVOICE_DISCOUNTING".equals(loan.getProductType())) {
                params = EncoreOpenLoanParams.forInvoiceDiscounting(
                        loan.getLoanNumber(),
                        borrowerName,
                        amount,
                        rate,
                        tenureDays,
                        productCode);
            } else {
                int tenureMonths = Math.max(1, (tenureDays + 29) / 30);
                params = EncoreOpenLoanParams.minimal(
                        loan.getLoanNumber(),
                        borrowerName,
                        amount,
                        rate,
                        tenureMonths,
                        productCode);
            }
            if (loan.getSanctionDate() != null) {
                params = params.withDisbursementDate(loan.getSanctionDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
            }

            var loanOd = plpEncoreLmsAdapter.buildLoanOdAccount(params, borrower);
            String requestJson = objectMapper.writeValueAsString(loanOd);
            String transactionId = "PLP-SANCTION-" + UUID.randomUUID().toString().substring(0, 8);
            log.info("[PLP][ENCORE] Sanction LMS open — loanNumber={} programId={} productCode={} productType={} "
                            + "transactionId={} borrowerId={} loanOdAccount={}",
                    loan.getLoanNumber(), loan.getProgramId(), productCode, loan.getProductType(),
                    transactionId, loan.getBorrowerId(), requestJson);
            String accountId = encoreLmsApi.openLoanAccountWithJson(transactionId, requestJson);

            loan.setLmsAccountId(accountId);
            mergeKfs(loan, "lmsAccountId", accountId);
            loanRepository.save(loan);
            refreshSummary(loan, accountId);
            recordOp(loan.getId(), OP_OPEN, accountId, STATUS_SUCCESS, requestJson, accountId, null);
            log.info("PLP LMS account opened loan={} accountId={}", loan.getLoanNumber(), accountId);
        } catch (Exception e) {
            log.error("PLP LMS open failed for {} (non-blocking): {}", loan.getLoanNumber(), e.getMessage(), e);
            recordOp(loan.getId(), OP_OPEN, null, STATUS_ERROR, null, null, e.getMessage());
        }
    }

    public void onLoanDisbursed(Loan loan) {
        ProgramLmsConfig cfg = programLmsConfigClient.fetch(loan.getProgramId());
        if (!cfg.isLmsEnabled() || !encoreLmsApi.isActive()) {
            return;
        }
        String accountId = resolveAccountId(loan);
        if (accountId == null) {
            log.warn("PLP LMS disburse skipped — no Encore account for {}", loan.getLoanNumber());
            return;
        }
        try {
            BigDecimal amount = loan.getDisbursedAmount() != null ? loan.getDisbursedAmount() : loan.getSanctionedAmount();
            EncoreOpenLoanParams ctx = EncoreOpenLoanParams.minimal(
                    loan.getLoanNumber(),
                    loan.getLoanNumber(),
                    amount,
                    loan.getInterestRate(),
                    Math.max(1, (loan.getTenureDays() + 29) / 30),
                    cfg.getEncoreProductCode());
            String txnId = encoreLmsApi.disburse(accountId, ctx);
            recordOp(loan.getId(), OP_DISBURSE, accountId, STATUS_SUCCESS, null, txnId, null);
            refreshSummary(loan, accountId);
        } catch (Exception e) {
            log.error("PLP LMS disburse failed for {} (non-blocking): {}", loan.getLoanNumber(), e.getMessage(), e);
            recordOp(loan.getId(), OP_DISBURSE, accountId, STATUS_ERROR, null, null, e.getMessage());
        }
    }

    public void onRepayment(Loan loan, BigDecimal repaidAmount) {
        ProgramLmsConfig cfg = programLmsConfigClient.fetch(loan.getProgramId());
        if (!cfg.isLmsEnabled() || !encoreLmsApi.isActive()) {
            return;
        }
        String accountId = resolveAccountId(loan);
        if (accountId == null) {
            log.warn("PLP LMS repay skipped — no Encore account for {}", loan.getLoanNumber());
            return;
        }
        try {
            log.info("PLP LMS repay: loan={} accountId={} amount={}", loan.getLoanNumber(), accountId, repaidAmount);
            String txnId = encoreLmsApi.repay(accountId, repaidAmount, "ScheduledRepayment");
            recordOp(loan.getId(), OP_REPAY, accountId, STATUS_SUCCESS, repaidAmount.toPlainString(), txnId, null);
            refreshSummary(loan, accountId);
        } catch (Exception e) {
            log.error("PLP LMS repay failed for {} (non-blocking): {}", loan.getLoanNumber(), e.getMessage(), e);
            recordOp(loan.getId(), OP_REPAY, accountId, STATUS_ERROR, null, null, e.getMessage());
        }
    }

    /**
     * Fetches the live payoff amount from Encore for a loan.
     * Returns null if LMS is not enabled, not active, or fetch fails.
     */
    public BigDecimal fetchPayoffAmount(Loan loan) {
        ProgramLmsConfig cfg = programLmsConfigClient.fetch(loan.getProgramId());
        if (!cfg.isLmsEnabled() || !encoreLmsApi.isActive()) {
            return null;
        }
        String accountId = resolveAccountId(loan);
        if (accountId == null) {
            return null;
        }
        try {
            JsonNode summary = encoreLmsApi.findSummaryFirstObject(accountId, true);
            if (summary == null) {
                return null;
            }
            // payOffAndDueAmount is the total closing amount (principal + interest + fees due)
            if (summary.has("payOffAndDueAmount")) {
                String val = summary.get("payOffAndDueAmount").asText();
                if (val != null && !val.isBlank()) {
                    BigDecimal payoff = new BigDecimal(val);
                    log.info("PLP LMS payoff fetched: loan={} accountId={} payOffAndDueAmount={}",
                            loan.getLoanNumber(), accountId, payoff);
                    return payoff;
                }
            }
            // Fallback to totalDemandDue if payOffAndDueAmount is absent
            if (summary.has("totalDemandDue")) {
                String val = summary.get("totalDemandDue").asText();
                if (val != null && !val.isBlank()) {
                    BigDecimal demandDue = new BigDecimal(val);
                    log.info("PLP LMS payoff (totalDemandDue): loan={} accountId={} totalDemandDue={}",
                            loan.getLoanNumber(), accountId, demandDue);
                    return demandDue;
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("PLP LMS payoff fetch failed for {} (returning null): {}", loan.getLoanNumber(), e.getMessage());
            return null;
        }
    }

    /**
     * Returns true if LMS is enabled and active for the given loan's program.
     */
    public boolean isLmsEnabledForLoan(Loan loan) {
        ProgramLmsConfig cfg = programLmsConfigClient.fetch(loan.getProgramId());
        return cfg.isLmsEnabled() && encoreLmsApi.isActive();
    }

    /**
     * Best-effort refresh of stored outstanding / payoff from Encore for list and detail reads.
     * Does not open, disburse, or repay — only pulls summary. Failures are logged and ignored.
     *
     * @return true if a summary refresh was attempted (account present and Encore active)
     */
    public boolean refreshOutstandingFromLms(Loan loan) {
        if (loan == null) {
            return false;
        }
        if (!encoreLmsApi.isActive()) {
            return false;
        }
        String accountId = resolveAccountId(loan);
        if (accountId == null) {
            return false;
        }
        // Skip program config round-trip when account already exists — account implies LMS path was used.
        refreshSummary(loan, accountId);
        return true;
    }

    private void refreshSummary(Loan loan, String accountId) {
        try {
            JsonNode summary = encoreLmsApi.findSummaryFirstObject(accountId, true);
            if (summary == null) {
                return;
            }
            LmsSummaryAmountSync.apply(loan, summary);
            if (summary.has("maturityDate")) {
                String md = summary.get("maturityDate").asText();
                if (md != null && !md.isBlank()) {
                    try {
                        loan.setDueDate(LocalDate.parse(md, DateTimeFormatter.ISO_LOCAL_DATE));
                    } catch (Exception ignored) {
                        /* keep local due date */
                    }
                }
            }
            BigDecimal lmsOutstanding = null;
            if (summary.has("payOffAndDueAmount")) {
                try {
                    lmsOutstanding = new BigDecimal(summary.get("payOffAndDueAmount").asText());
                } catch (Exception ignored) { }
            }
            if (lmsOutstanding == null && summary.has("totalDemandDue")) {
                try {
                    lmsOutstanding = new BigDecimal(summary.get("totalDemandDue").asText());
                } catch (Exception ignored) { }
            }
            if (lmsOutstanding != null) {
                loan.setOutstandingAmount(lmsOutstanding);
                mergeKfs(loan, "lmsOutstanding", lmsOutstanding.toPlainString());
            }
            mergeKfs(loan, "lmsSummary", summary.toString());
            loanRepository.save(loan);
            recordOp(loan.getId(), OP_SUMMARY, accountId, STATUS_SUCCESS, null, summary.toString(), null);
        } catch (Exception e) {
            log.warn("PLP LMS summary refresh failed for {}: {}", loan.getLoanNumber(), e.getMessage());
            if (LmsPayableAmounts.shouldUsePrincipalFallback(loan)) {
                LmsPayableAmounts.applyPrincipalFallback(loan);
                loanRepository.save(loan);
                log.info("PLP LMS summary unavailable — using principal-based outstanding for {}: {}",
                        loan.getLoanNumber(), loan.getOutstandingAmount());
            }
        }
    }

    private String resolveAccountId(Loan loan) {
        if (loan.getLmsAccountId() != null && !loan.getLmsAccountId().isBlank()) {
            return loan.getLmsAccountId();
        }
        if (loan.getKfsData() != null) {
            Object v = loan.getKfsData().get("lmsAccountId");
            if (v != null && !String.valueOf(v).isBlank()) {
                return String.valueOf(v);
            }
        }
        return null;
    }

    private void mergeKfs(Loan loan, String key, String value) {
        Map<String, Object> kfs = loan.getKfsData() == null ? new HashMap<>() : new HashMap<>(loan.getKfsData());
        kfs.put(key, value);
        loan.setKfsData(kfs);
    }

    private void recordOp(UUID loanId, String operation, String accountId, String status,
                          String requestJson, String responseJson, String error) {
        lmsLoanOperationRepository.save(LmsLoanOperation.builder()
                .loanId(loanId)
                .operation(operation)
                .encoreAccountId(accountId)
                .status(status)
                .requestJson(requestJson)
                .responseJson(responseJson)
                .errorMessage(error)
                .build());
    }

    private static String stringVal(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }
}
