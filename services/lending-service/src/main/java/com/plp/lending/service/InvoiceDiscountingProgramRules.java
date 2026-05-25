package com.plp.lending.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Evaluates invoice-level eligibility rules stored in {@code Program.config} for Invoice Discounting.
 */
public final class InvoiceDiscountingProgramRules {

    public static final String MSG_INVOICE_TOO_OLD = "Invoice is older than allowed maximum age";
    public static final String MSG_AMOUNT_BELOW_MIN = "Invoice amount is below minimum allowed amount";
    public static final String MSG_DUE_TOO_SOON = "Invoice due date is earlier than minimum required due date";
    public static final String MSG_INVOICE_DATA_INCOMPLETE = "Invoice data incomplete for eligibility rules";

    private InvoiceDiscountingProgramRules() {
    }

    /**
     * Keys: {@code maxInvoiceAgeDays}, {@code minInvoiceAmount}, {@code minDaysToDueDate} — omitted or unparsable entries are ignored.
     */
    public static List<String> evaluate(
            LocalDate today,
            LocalDate invoiceDate,
            LocalDate dueDate,
            BigDecimal invoiceAmount,
            Map<String, Object> programConfig) {
        if (programConfig == null || programConfig.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> reasons = new ArrayList<>();
        if (invoiceDate == null || dueDate == null || invoiceAmount == null) {
            reasons.add(MSG_INVOICE_DATA_INCOMPLETE);
            return reasons;
        }

        Integer maxInvoiceAgeDays = getNonNegativeInteger(programConfig.get("maxInvoiceAgeDays"));
        if (maxInvoiceAgeDays != null) {
            LocalDate oldestAllowed = today.minusDays(maxInvoiceAgeDays);
            if (invoiceDate.isBefore(oldestAllowed)) {
                reasons.add(MSG_INVOICE_TOO_OLD);
            }
        }

        BigDecimal minInvoiceAmount = getBigDecimal(programConfig.get("minInvoiceAmount"));
        if (minInvoiceAmount != null && invoiceAmount.compareTo(minInvoiceAmount) < 0) {
            reasons.add(MSG_AMOUNT_BELOW_MIN);
        }

        Integer minDaysToDueDate = getNonNegativeInteger(programConfig.get("minDaysToDueDate"));
        if (minDaysToDueDate != null) {
            LocalDate minimumDueDate = today.plusDays(minDaysToDueDate);
            if (dueDate.isBefore(minimumDueDate)) {
                reasons.add(MSG_DUE_TOO_SOON);
            }
        }

        return reasons;
    }

    private static Integer getNonNegativeInteger(Object raw) {
        Integer n = parseInteger(raw);
        return n != null && n >= 0 ? n : null;
    }

    private static Integer parseInteger(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number num) {
            return num.intValue();
        }
        String s = raw.toString().trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal getBigDecimal(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof BigDecimal bd) {
            return bd;
        }
        if (raw instanceof Number num) {
            return BigDecimal.valueOf(num.doubleValue());
        }
        String s = raw.toString().trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static LocalDate parseLocalDate(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof LocalDate d) {
            return d;
        }
        if (raw instanceof java.time.Instant instant) {
            return instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        }
        try {
            return LocalDate.parse(raw.toString());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    static BigDecimal parseInvoiceAmount(Object raw) {
        return getBigDecimal(raw);
    }
}
