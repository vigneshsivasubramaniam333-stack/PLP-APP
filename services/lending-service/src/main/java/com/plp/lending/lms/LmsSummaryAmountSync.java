package com.plp.lending.lms;

import com.fasterxml.jackson.databind.JsonNode;
import com.plp.lending.model.entity.Loan;
import com.plp.lending.model.enums.LoanStatus;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Maps Encore findSummary fields onto PLP loan amount columns when LMS is the source of truth.
 */
final class LmsSummaryAmountSync {

    private LmsSummaryAmountSync() {
    }

    static void apply(Loan loan, JsonNode summary) {
        if (loan == null || summary == null || summary.isNull()) {
            return;
        }
        BigDecimal principal = firstDecimal(summary,
                "amountMagnitude", "accountBalance", "principalOutstanding", "sanctionedAmount");
        BigDecimal payoff = firstDecimal(summary, "payOffAndDueAmount", "totalDemandDue");
        BigDecimal interestDue = firstDecimal(summary, "totalNormalInterestDue", "normalInterestDue");

        if (principal != null && principal.compareTo(BigDecimal.ZERO) > 0) {
            if (loan.getStatus() == LoanStatus.DISBURSED
                    || loan.getStatus() == LoanStatus.REPAYMENT_DUE
                    || loan.getStatus() == LoanStatus.OVERDUE
                    || loan.getStatus() == LoanStatus.CLOSED) {
                loan.setDisbursedAmount(principal);
            }
            loan.setSanctionedAmount(principal);
            if (loan.getStatus() == LoanStatus.REQUESTED) {
                loan.setRequestedAmount(principal);
            }
        }

        if (interestDue != null && interestDue.compareTo(BigDecimal.ZERO) >= 0) {
            loan.setInterestAmount(interestDue);
        }

        if (payoff != null && payoff.compareTo(BigDecimal.ZERO) >= 0) {
            loan.setOutstandingAmount(payoff);
            loan.setTotalRepayable(payoff.add(
                    loan.getTotalRepaid() != null ? loan.getTotalRepaid() : BigDecimal.ZERO));
        } else if (principal != null && interestDue != null) {
            loan.setTotalRepayable(principal.add(interestDue));
            if (loan.getOutstandingAmount() == null
                    || loan.getOutstandingAmount().compareTo(BigDecimal.ZERO) == 0) {
                loan.setOutstandingAmount(loan.getTotalRepayable().subtract(
                        loan.getTotalRepaid() != null ? loan.getTotalRepaid() : BigDecimal.ZERO));
            }
        }
    }

    private static BigDecimal firstDecimal(JsonNode node, String... fieldNames) {
        for (String field : fieldNames) {
            Optional<BigDecimal> parsed = decimalField(node, field);
            if (parsed.isPresent()) {
                return parsed.get();
            }
        }
        return null;
    }

    private static Optional<BigDecimal> decimalField(JsonNode node, String field) {
        if (!node.has(field) || node.get(field).isNull()) {
            return Optional.empty();
        }
        String raw = node.get(field).asText("").trim();
        if (raw.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BigDecimal(raw));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
