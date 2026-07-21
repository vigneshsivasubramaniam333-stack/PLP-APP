package com.plp.lending.lms;

import com.plp.lending.model.entity.Loan;
import com.plp.lending.model.enums.LoanStatus;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Resolves payable balances when Encore findSummaries is unavailable but an LMS account exists.
 * Before LMS sync, PLP stores principal + projected interest on {@code outstandingAmount}; that estimate
 * must not be shown once disbursement has posted to Encore.
 * <p>
 * After a successful LMS summary sync, {@code outstandingAmount} is Encore {@code payOffAndDueAmount}
 * (principal + interest + fees) and must be kept — do not strip back to principal.
 */
public final class LmsPayableAmounts {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private LmsPayableAmounts() {
    }

    static boolean hasLmsAccount(Loan loan) {
        return loan != null
                && loan.getLmsAccountId() != null
                && !loan.getLmsAccountId().isBlank();
    }

    static boolean isDisbursedOrDue(Loan loan) {
        if (loan == null || loan.getStatus() == null) {
            return false;
        }
        return switch (loan.getStatus()) {
            case DISBURSED, REPAYMENT_DUE, OVERDUE, CLOSED -> true;
            default -> false;
        };
    }

    static BigDecimal principalBase(Loan loan) {
        if (loan.getDisbursedAmount() != null && loan.getDisbursedAmount().compareTo(ZERO) > 0) {
            return loan.getDisbursedAmount();
        }
        if (loan.getSanctionedAmount() != null && loan.getSanctionedAmount().compareTo(ZERO) > 0) {
            return loan.getSanctionedAmount();
        }
        return loan.getRequestedAmount();
    }

    public static BigDecimal principalOutstanding(Loan loan) {
        BigDecimal principal = principalBase(loan);
        if (principal == null) {
            return ZERO;
        }
        BigDecimal repaid = loan.getTotalRepaid() != null ? loan.getTotalRepaid() : ZERO;
        return principal.subtract(repaid).max(ZERO);
    }

    /**
     * True when stored outstanding still reflects the pre-LMS finance estimate (principal + projected interest/fees)
     * and Encore summary has never been successfully applied to this loan.
     */
    public static boolean shouldUsePrincipalFallback(Loan loan) {
        if (!hasLmsAccount(loan) || !isDisbursedOrDue(loan)) {
            return false;
        }
        // Successful findSummaries sync writes lmsOutstanding / lmsSummary into kfsData.
        // After that, outstanding may legitimately exceed principal (includes interest).
        if (hasSuccessfulLmsAmountSync(loan)) {
            return false;
        }
        BigDecimal principal = principalBase(loan);
        BigDecimal stored = loan.getOutstandingAmount();
        if (principal == null) {
            return true;
        }
        if (stored == null) {
            return true;
        }
        return stored.compareTo(principal) > 0;
    }

    static boolean hasSuccessfulLmsAmountSync(Loan loan) {
        Map<String, Object> kfs = loan.getKfsData();
        if (kfs == null || kfs.isEmpty()) {
            return false;
        }
        Object outstanding = kfs.get("lmsOutstanding");
        if (outstanding != null && !String.valueOf(outstanding).isBlank()) {
            return true;
        }
        Object summary = kfs.get("lmsSummary");
        return summary != null && !String.valueOf(summary).isBlank();
    }

    public static void applyPrincipalFallback(Loan loan) {
        BigDecimal payable = principalOutstanding(loan);
        loan.setOutstandingAmount(payable);
        BigDecimal repaid = loan.getTotalRepaid() != null ? loan.getTotalRepaid() : ZERO;
        loan.setTotalRepayable(payable.add(repaid));
    }
}
