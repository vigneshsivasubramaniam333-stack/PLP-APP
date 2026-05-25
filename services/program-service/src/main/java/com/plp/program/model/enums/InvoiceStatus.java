package com.plp.program.model.enums;

/**
 * Persisted invoice {@code status} values (VARCHAR). Use {@link #name()} when writing to the entity.
 */
public enum InvoiceStatus {
    UPLOADED,
    VERIFIED,
    ELIGIBLE,
    BORROWER_ACCEPTED,
    /** Borrower submitted a financing request; lending row created. */
    FINANCING_REQUESTED,
    PARTIALLY_DISCOUNTED,
    FULLY_DISCOUNTED
}
