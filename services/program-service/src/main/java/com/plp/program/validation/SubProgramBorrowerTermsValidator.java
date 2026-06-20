package com.plp.program.validation;

import com.plp.program.model.entity.SubProgram;
import com.plp.program.model.entity.SubProgramBorrower;

import java.math.BigDecimal;
import java.util.Set;

public final class SubProgramBorrowerTermsValidator {

    private static final Set<String> PAYMENT_METHODS = Set.of("SMART_COLLECT");

    private SubProgramBorrowerTermsValidator() {
    }

    public static void validateAndApplyDefaults(SubProgramBorrower membership, SubProgram subProgram) {
        if (membership.getDiscountHold() == null || membership.getDiscountHold().isBlank()) {
            membership.setDiscountHold("NO");
        } else {
            membership.setDiscountHold(parseYesNo(membership.getDiscountHold()));
        }
        if (membership.getPaymentMethod() == null || membership.getPaymentMethod().isBlank()) {
            membership.setPaymentMethod("SMART_COLLECT");
        } else {
            String method = membership.getPaymentMethod().trim().toUpperCase();
            if (!PAYMENT_METHODS.contains(method)) {
                throw new IllegalArgumentException("Unsupported paymentMethod: " + method);
            }
            membership.setPaymentMethod(method);
        }
        if (membership.getOverdueInterestRate() == null) {
            membership.setOverdueInterestRate(BigDecimal.ZERO);
        } else {
            validateRate(membership.getOverdueInterestRate(), "overdueInterestRate");
        }
        if (membership.getInterestRate() != null) {
            validateRate(membership.getInterestRate(), "interestRate");
            if (subProgram.getInterestRate() != null
                    && membership.getInterestRate().compareTo(subProgram.getInterestRate()) > 0) {
                throw new IllegalArgumentException("interestRate cannot exceed sub-program rate");
            }
        }
        if (membership.getDiscountMarginPercent() != null) {
            validateRate(membership.getDiscountMarginPercent(), "discountMarginPercent");
            if (subProgram.getMarginPercent() != null
                    && membership.getDiscountMarginPercent().compareTo(subProgram.getMarginPercent()) > 0) {
                throw new IllegalArgumentException("discountMarginPercent cannot exceed sub-program margin");
            }
        }
        if (membership.getCreditPeriodDays() != null && membership.getCreditPeriodDays() < 0) {
            throw new IllegalArgumentException("creditPeriodDays must be >= 0");
        }
        if (membership.getCreditPeriodDays() != null
                && subProgram.getMaxTenureDays() != null
                && membership.getCreditPeriodDays() > subProgram.getMaxTenureDays()) {
            throw new IllegalArgumentException("creditPeriodDays cannot exceed sub-program maxTenureDays");
        }
    }

    private static String parseYesNo(String raw) {
        String s = raw.trim().toUpperCase();
        if ("YES".equals(s) || "NO".equals(s)) {
            return s;
        }
        throw new IllegalArgumentException("discountHold must be YES or NO");
    }

    private static void validateRate(BigDecimal rate, String field) {
        if (rate.compareTo(BigDecimal.ZERO) < 0 || rate.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException(field + " must be between 0 and 100");
        }
    }
}
