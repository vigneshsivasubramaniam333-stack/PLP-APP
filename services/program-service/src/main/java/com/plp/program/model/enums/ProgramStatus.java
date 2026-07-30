package com.plp.program.model.enums;

public enum ProgramStatus {
    DRAFT,
    PENDING_L2,
    SENT_BACK,
    /** L2 approved; waiting for Operations document verification before ACTIVE */
    APPROVED_PENDING_DOCS,
    ACTIVE,
    PAUSED,
    CLOSED
}
