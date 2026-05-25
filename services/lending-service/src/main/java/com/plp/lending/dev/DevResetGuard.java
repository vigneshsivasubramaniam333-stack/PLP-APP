package com.plp.lending.dev;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Locale;

@Component
public class DevResetGuard {

    public static final String MSG_DISABLED = "Dev reset is disabled";

    private final Environment environment;

    @Value("${plp.dev-reset.enabled:false}")
    private boolean propertyEnabled;

    public DevResetGuard(Environment environment) {
        this.environment = environment;
    }

    public boolean isDevResetEnabled() {
        if (propertyEnabled) {
            return true;
        }
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> "local".equalsIgnoreCase(p) || "dev".equalsIgnoreCase(p));
    }

    public void requireDevResetEnabled() {
        if (!isDevResetEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, MSG_DISABLED);
        }
    }

    public static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";

    public static void requirePlatformAdminHeader(String rolesHeader) {
        if (rolesHeader == null || rolesHeader.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only PLATFORM_ADMIN can run dev reset");
        }
        boolean ok = Arrays.stream(rolesHeader.split(","))
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .anyMatch(PLATFORM_ADMIN::equals);
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only PLATFORM_ADMIN can run dev reset");
        }
    }
}
