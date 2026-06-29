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
    FULLY_DISCOUNTED,
    /** Finance request rejected by lender (loan REJECTED). */
    REJECTED,
    /** All linked loans repaid / closed. */
    CLOSED,
    EXPIRED,
    /** SBD Early Pay: borrower requested anchor-funded discount (legacy DISCOUNTED_EP). */
    DISCOUNTED_EP,
    /** SBD Early Pay: anchor approved request (legacy SANCTIONED_EP). */
    SANCTIONED_EP
}
