package com.plp.program.validation;

import java.util.Set;

public final class RepaymentMechanismValidator {

    public static final Set<String> MECHANISMS = Set.of("SMART_COLLECT", "PAYU_PG", "API_PG");

    private RepaymentMechanismValidator() {}

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "SMART_COLLECT";
        }
        String value = raw.trim().toUpperCase();
        if (!MECHANISMS.contains(value)) {
            throw new IllegalArgumentException("Unsupported repayment mechanism: " + value);
        }
        return value;
    }
}
