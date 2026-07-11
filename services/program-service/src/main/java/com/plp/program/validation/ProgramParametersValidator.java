package com.plp.program.validation;

import com.plp.program.model.enums.ProductType;

import java.util.HashMap;
import java.util.Map;

/**
 * Validates and normalizes {@link com.plp.program.model.entity.Program#getParameters()} JSON.
 */
public final class ProgramParametersValidator {

    private ProgramParametersValidator() {
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

    public static Map<String, Object> mergeWithDefaults(Map<String, Object> incoming) {
        Map<String, Object> merged = defaultParameters();
        if (incoming != null) {
            merged.putAll(incoming);
        }
        return validateAndNormalize(merged, null);
    }

    public static Map<String, Object> validateAndNormalize(Map<String, Object> parameters, ProductType productType) {
        if (parameters == null) {
            parameters = defaultParameters();
        }
        Map<String, Object> out = new HashMap<>(parameters);

        out.put("enablePaymentForBorrower", parseYesNo(out.get("enablePaymentForBorrower"), false));
        out.put("autoDiscounting", parseYesNo(out.get("autoDiscounting"), false));
        out.put("autoPullOption", parseYesNo(out.get("autoPullOption"), false));
        out.put("intFreeCreditPeriod", parseYesNo(out.get("intFreeCreditPeriod"), false));
        out.put("autoPaymentBorrower", parseYesNo(out.get("autoPaymentBorrower"), false));
        out.put("autoAcceptInvoices", parseYesNo(out.get("autoAcceptInvoices"), false));
        out.put("partialDiscount", parseYesNo(out.get("partialDiscount"), false));
        out.put("invoiceDelete", parseYesNo(out.get("invoiceDelete"), false));

        out.put("discountingDay", parseNonNegativeInt(out.get("discountingDay"), 0));
        out.put("gapBetweenDiscountingDays", parseNonNegativeInt(out.get("gapBetweenDiscountingDays"), 0));
        out.put("gapBetweenPreviousInvoiceDays", parseNonNegativeInt(out.get("gapBetweenPreviousInvoiceDays"), 0));
        out.put("gapBetweenSanctionAndDisbursementDays",
                parseNonNegativeInt(out.get("gapBetweenSanctionAndDisbursementDays"), 0));
        out.put("intFreePeriodDays", parseNonNegativeInt(out.get("intFreePeriodDays"), 0));

        String sanctionType = parseSanctionType(out.get("sanctionType"));
        out.put("sanctionType", sanctionType);

        boolean autoDiscounting = Boolean.TRUE.equals(out.get("autoDiscounting"));
        int discountingDay = (Integer) out.get("discountingDay");
        if (autoDiscounting && (discountingDay < 0 || discountingDay > 28)) {
            throw new IllegalArgumentException("discountingDay must be between 0 and 28 when autoDiscounting is enabled");
        }

        boolean intFree = Boolean.TRUE.equals(out.get("intFreeCreditPeriod"));
        int intFreeDays = (Integer) out.get("intFreePeriodDays");
        if (intFree && intFreeDays <= 0) {
            throw new IllegalArgumentException("intFreePeriodDays must be greater than 0 when intFreeCreditPeriod is enabled");
        }

        return out;
    }

    public static Map<String, Object> defaultConfig(ProductType productType) {
        Map<String, Object> config = new HashMap<>();
        if (productType == ProductType.INVOICE_DISCOUNTING) {
            config.put("maxInvoiceAgeDays", 90);
        }
        return config;
    }

    public static Map<String, Object> mergeConfigWithDefaults(Map<String, Object> incoming, ProductType productType) {
        Map<String, Object> merged = defaultConfig(productType);
        if (incoming != null) {
            merged.putAll(incoming);
        }
        return validateConfig(merged, productType);
    }

    public static Map<String, Object> validateConfig(Map<String, Object> config, ProductType productType) {
        if (config == null) {
            config = new HashMap<>();
        }
        Map<String, Object> out = new HashMap<>(config);
        if (out.containsKey("maxInvoiceAgeDays") && out.get("maxInvoiceAgeDays") != null) {
            int age = parsePositiveInt(out.get("maxInvoiceAgeDays"), "maxInvoiceAgeDays");
            out.put("maxInvoiceAgeDays", age);
        } else if (productType == ProductType.INVOICE_DISCOUNTING) {
            out.put("maxInvoiceAgeDays", 90);
        }
        if (out.containsKey("minInvoiceAmount") && out.get("minInvoiceAmount") != null) {
            parseNonNegativeDecimal(out.get("minInvoiceAmount"), "minInvoiceAmount");
        }
        if (out.containsKey("minDaysToDueDate") && out.get("minDaysToDueDate") != null) {
            parseNonNegativeInt(out.get("minDaysToDueDate"), 0);
        }
        if (out.containsKey("dependencyVintagePercent") && out.get("dependencyVintagePercent") != null) {
            parseNonNegativeDecimal(out.get("dependencyVintagePercent"), "dependencyVintagePercent");
        }
        if (out.containsKey("anchorRelationshipVintageMonths") && out.get("anchorRelationshipVintageMonths") != null) {
            parsePositiveInt(out.get("anchorRelationshipVintageMonths"), "anchorRelationshipVintageMonths");
        }
        return out;
    }

    public static boolean parseYesNo(Object raw, boolean defaultValue) {
        if (raw == null) {
            return defaultValue;
        }
        if (raw instanceof Boolean b) {
            return b;
        }
        String s = raw.toString().trim().toUpperCase();
        if ("YES".equals(s) || "TRUE".equals(s)) {
            return true;
        }
        if ("NO".equals(s) || "FALSE".equals(s)) {
            return false;
        }
        throw new IllegalArgumentException("Expected YES/NO boolean flag, got: " + raw);
    }

    private static String parseSanctionType(Object raw) {
        if (raw == null) {
            return "MANUAL";
        }
        String s = raw.toString().trim().toUpperCase();
        if ("MANUAL".equals(s) || "AUTO".equals(s)) {
            return s;
        }
        throw new IllegalArgumentException("sanctionType must be MANUAL or AUTO");
    }

    private static int parseNonNegativeInt(Object raw, int defaultValue) {
        if (raw == null) {
            return defaultValue;
        }
        int n = parseInt(raw);
        if (n < 0) {
            throw new IllegalArgumentException("Day count must be >= 0");
        }
        return n;
    }

    private static int parsePositiveInt(Object raw, String field) {
        int n = parseInt(raw);
        if (n < 1) {
            throw new IllegalArgumentException(field + " must be >= 1");
        }
        return n;
    }

    private static int parseInt(Object raw) {
        if (raw instanceof Number num) {
            return num.intValue();
        }
        try {
            return Integer.parseInt(raw.toString().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer: " + raw);
        }
    }

    private static void parseNonNegativeDecimal(Object raw, String field) {
        try {
            double d = raw instanceof Number n ? n.doubleValue() : Double.parseDouble(raw.toString().trim());
            if (d < 0) {
                throw new IllegalArgumentException(field + " must be >= 0");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number for " + field + ": " + raw);
        }
    }
}
