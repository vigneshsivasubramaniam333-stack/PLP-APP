package com.plp.iam.model.converter;

import com.plp.iam.model.enums.UserRole;

import java.util.Locale;

/**
 * Temporary compatibility: persisted values {@code PROGRAM_MANAGER} / {@code TREASURY} map to renamed roles.
 */
public final class UserRoleLegacyMapping {

    private UserRoleLegacyMapping() {}

    public static UserRole fromLegacyDbString(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        if ("PROGRAM_MANAGER".equalsIgnoreCase(s)) {
            return UserRole.CREDIT_MANAGER;
        }
        if ("TREASURY".equalsIgnoreCase(s)) {
            return UserRole.ACCOUNTS_OFFICER;
        }
        return UserRole.valueOf(s.toUpperCase(Locale.ROOT));
    }
}
