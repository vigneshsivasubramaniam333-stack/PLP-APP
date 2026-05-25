package com.plp.lending.audit;

/** Gateway-forwarded headers captured on audit records. */
public final class AuditHeaders {

    public static final String X_USER_ID = "X-User-Id";
    public static final String X_USER_ROLES = "X-User-Roles";
    public static final String X_LINKED_ENTITY_ID = "X-Linked-Entity-Id";
    public static final String X_LINKED_ENTITY_TYPE = "X-Linked-Entity-Type";

    private AuditHeaders() {}
}
