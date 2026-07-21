package com.plp.lending.lms;

import com.plp.lending.model.entity.Loan;
import com.plp.lending.model.enums.LoanStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LmsPayableAmountsTest {

    @Test
    void shouldUsePrincipalFallbackWhenStoredOutstandingExceedsDisbursed() {
        Loan loan = baseLoan();
        loan.setOutstandingAmount(new BigDecimal("2059.18"));
        loan.setTotalRepayable(new BigDecimal("2059.18"));
        assertTrue(LmsPayableAmounts.shouldUsePrincipalFallback(loan));
        assertEquals(new BigDecimal("2000.00"), LmsPayableAmounts.principalOutstanding(loan));
    }

    @Test
    void shouldNotUsePrincipalFallbackAfterSuccessfulLmsSyncEvenWhenPayoffExceedsPrincipal() {
        Loan loan = baseLoan();
        loan.setOutstandingAmount(new BigDecimal("1201.00"));
        loan.setInterestAmount(new BigDecimal("1.17"));
        Map<String, Object> kfs = new LinkedHashMap<>();
        kfs.put("lmsOutstanding", "1201.00");
        loan.setKfsData(kfs);
        assertFalse(LmsPayableAmounts.shouldUsePrincipalFallback(loan));
    }

    @Test
    void shouldNotUsePrincipalFallbackWithoutLmsAccount() {
        Loan loan = baseLoan();
        loan.setLmsAccountId(null);
        loan.setOutstandingAmount(new BigDecimal("2059.18"));
        assertFalse(LmsPayableAmounts.shouldUsePrincipalFallback(loan));
    }

    @Test
    void applyPrincipalFallbackUpdatesOutstandingAndTotalRepayable() {
        Loan loan = baseLoan();
        loan.setOutstandingAmount(new BigDecimal("2059.18"));
        loan.setTotalRepayable(new BigDecimal("2059.18"));
        LmsPayableAmounts.applyPrincipalFallback(loan);
        assertEquals(new BigDecimal("2000.00"), loan.getOutstandingAmount());
        assertEquals(new BigDecimal("2000.00"), loan.getTotalRepayable());
    }

    private static Loan baseLoan() {
        return Loan.builder()
                .id(UUID.randomUUID())
                .borrowerId(UUID.randomUUID())
                .programId(UUID.randomUUID())
                .anchorId(UUID.randomUUID())
                .productType("INVOICE_DISCOUNTING")
                .requestedAmount(new BigDecimal("2000.00"))
                .sanctionedAmount(new BigDecimal("2000.00"))
                .disbursedAmount(new BigDecimal("2000.00"))
                .totalRepaid(BigDecimal.ZERO)
                .status(LoanStatus.DISBURSED)
                .lmsAccountId("000000025585")
                .tenureDays(30)
                .build();
    }
}
