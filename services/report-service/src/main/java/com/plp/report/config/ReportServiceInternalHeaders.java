package com.plp.report.config;

import org.springframework.http.HttpHeaders;

/** Trusted service-to-service headers for report aggregation calls. */
public final class ReportServiceInternalHeaders {

    private static final String INTERNAL_ACTOR_ROLES = "PLATFORM_ADMIN";
    private static final String INTERNAL_ACTOR_USER_ID = "SYSTEM";

    private ReportServiceInternalHeaders() {}

    public static HttpHeaders trustedInternalHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-User-Roles", INTERNAL_ACTOR_ROLES);
        h.set("X-User-Id", INTERNAL_ACTOR_USER_ID);
        return h;
    }
}
