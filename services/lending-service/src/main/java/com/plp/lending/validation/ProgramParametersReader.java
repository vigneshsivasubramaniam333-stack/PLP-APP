package com.plp.lending.validation;

import java.util.HashMap;
import java.util.Map;

/** Mirrors program-service {@code ProgramParametersValidator} defaults for lending-side reads. */
public final class ProgramParametersReader {

    private ProgramParametersReader() {
    }

    public static Map<String, Object> defaultParameters() {
        Map<String, Object> p = new HashMap<>();
        p.put("enablePaymentForBorrower", false);
        p.put("autoDiscounting", false);
        p.put("discountingDay", 0);
        p.put("gapBetweenDiscountingDays", 0);
        p.put("gapBetweenPreviousInvoiceDays", 0);
        p.put("autoPullOption", false);
        p.put("gapBetweenSanctionAndDisbursementDays", 0);
        p.put("intFreeCreditPeriod", false);
        p.put("intFreePeriodDays", 0);
        p.put("autoPaymentBorrower", false);
        p.put("autoAcceptInvoices", false);
        p.put("sanctionType", "MANUAL");
        p.put("partialDiscount", false);
        p.put("invoiceDelete", false);
        return p;
    }

    public static Map<String, Object> normalize(Map<String, Object> incoming) {
        Map<String, Object> merged = defaultParameters();
        if (incoming != null) {
            merged.putAll(incoming);
        }
        merged.put("enablePaymentForBorrower", parseYesNo(merged.get("enablePaymentForBorrower")));
        merged.put("autoDiscounting", parseYesNo(merged.get("autoDiscounting")));
        merged.put("autoPullOption", parseYesNo(merged.get("autoPullOption")));
        merged.put("intFreeCreditPeriod", parseYesNo(merged.get("intFreeCreditPeriod")));
        merged.put("autoPaymentBorrower", parseYesNo(merged.get("autoPaymentBorrower")));
        merged.put("autoAcceptInvoices", parseYesNo(merged.get("autoAcceptInvoices")));
        merged.put("partialDiscount", parseYesNo(merged.get("partialDiscount")));
        merged.put("invoiceDelete", parseYesNo(merged.get("invoiceDelete")));
        merged.put("discountingDay", parseInt(merged.get("discountingDay"), 0));
        merged.put("gapBetweenDiscountingDays", parseInt(merged.get("gapBetweenDiscountingDays"), 0));
        merged.put("gapBetweenPreviousInvoiceDays", parseInt(merged.get("gapBetweenPreviousInvoiceDays"), 0));
        merged.put("gapBetweenSanctionAndDisbursementDays", parseInt(merged.get("gapBetweenSanctionAndDisbursementDays"), 0));
        merged.put("intFreePeriodDays", parseInt(merged.get("intFreePeriodDays"), 0));
        Object sanction = merged.get("sanctionType");
        merged.put("sanctionType", sanction != null ? sanction.toString().trim().toUpperCase() : "MANUAL");
        return merged;
    }

    public static boolean parseYesNo(Object raw) {
        if (raw instanceof Boolean b) {
            return b;
        }
        if (raw == null) {
            return false;
        }
        String s = raw.toString().trim().toUpperCase();
        return "YES".equals(s) || "TRUE".equals(s);
    }

    private static int parseInt(Object raw, int defaultValue) {
        if (raw == null) {
            return defaultValue;
        }
        if (raw instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(raw.toString().trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
