package com.plp.lending.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceDiscountingProgramRulesTest {

    @Test
    void creditPeriodWithinMaxPasses() {
        LocalDate invoiceDate = LocalDate.of(2026, 1, 1);
        LocalDate dueDate = LocalDate.of(2026, 3, 1);
        var reasons = InvoiceDiscountingProgramRules.evaluate(
                LocalDate.of(2026, 1, 10),
                invoiceDate,
                dueDate,
                new BigDecimal("10000"),
                Map.of("maxInvoiceAgeDays", 90));
        assertThat(reasons).isEmpty();
    }

    @Test
    void creditPeriodFailsWhenExceedsMax() {
        LocalDate invoiceDate = LocalDate.of(2026, 1, 1);
        LocalDate dueDate = LocalDate.of(2026, 6, 1);
        var reasons = InvoiceDiscountingProgramRules.evaluate(
                LocalDate.of(2026, 1, 10),
                invoiceDate,
                dueDate,
                new BigDecimal("10000"),
                Map.of("maxInvoiceAgeDays", 90));
        assertThat(reasons).contains(InvoiceDiscountingProgramRules.MSG_INVOICE_CREDIT_PERIOD_EXCEEDED);
    }

    @Test
    void defaultsMaxInvoiceAgeTo90WhenAbsent() {
        LocalDate invoiceDate = LocalDate.of(2026, 1, 1);
        LocalDate dueDate = LocalDate.of(2026, 6, 1);
        var reasons = InvoiceDiscountingProgramRules.evaluate(
                LocalDate.of(2026, 1, 10),
                invoiceDate,
                dueDate,
                new BigDecimal("10000"),
                Map.of());
        assertThat(reasons).contains(InvoiceDiscountingProgramRules.MSG_INVOICE_CREDIT_PERIOD_EXCEEDED);
    }
}
