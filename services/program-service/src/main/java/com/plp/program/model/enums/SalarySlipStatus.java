package com.plp.program.model.enums;

/**
 * Persisted lifecycle for a salary slip (Pay Day). Kept aligned with lending eligibility labels where possible.
 */
public enum SalarySlipStatus {
    AVAILABLE_FOR_LOAN,
    LOAN_REQUESTED,
    REJECTED_AVAILABLE_AGAIN,
    DISBURSED_USED,
    CLOSED_USED
}
