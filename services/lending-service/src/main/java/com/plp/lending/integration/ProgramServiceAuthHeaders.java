package com.plp.lending.integration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * Headers for trusted lending-service → program-service calls after tenant guards were added.
 * Acts as an internal lender-equivalent actor; does not weaken program-service authorization rules.
 */
public final class ProgramServiceAuthHeaders {

    public static final String HEADER_USER_ROLES = "X-User-Roles";
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_EMAIL = "X-User-Email";

    private static final String INTERNAL_ACTOR_ROLES = "PLATFORM_ADMIN";
    private static final String INTERNAL_ACTOR_USER_ID = "SYSTEM";
    private static final String INTERNAL_ACTOR_EMAIL = "system@plp.internal";

    private ProgramServiceAuthHeaders() {}

    /** Read-only or POST-without-body calls (block/release use JSON-less POST). */
    public static HttpHeaders trustedInternalHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.set(HEADER_USER_ROLES, INTERNAL_ACTOR_ROLES);
        h.set(HEADER_USER_ID, INTERNAL_ACTOR_USER_ID);
        h.set(HEADER_USER_EMAIL, INTERNAL_ACTOR_EMAIL);
        return h;
    }

    /** POST/PATCH with a JSON body. */
    public static HttpHeaders trustedInternalJsonHeaders() {
        HttpHeaders h = trustedInternalHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }
}
