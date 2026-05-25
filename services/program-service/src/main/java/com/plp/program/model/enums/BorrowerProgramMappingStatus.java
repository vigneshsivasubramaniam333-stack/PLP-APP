package com.plp.program.model.enums;

/** Lifecycle for LOS-sourced borrower ↔ program enrollment (approval remains internal). */
public enum BorrowerProgramMappingStatus {
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    CANCELLED
}
