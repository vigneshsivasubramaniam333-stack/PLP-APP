package com.plp.program.model.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Lender onboarding: borrower master record with optional immediate sub-program membership.
 */
@Data
public class BorrowerCreateRequest {

    /** Optional client-supplied code; generated when blank */
    private String borrowerCode;

    private String name;
    private String email;
    private String phone;

    /** Required when {@code subProgramId} is null (legacy). */
    private UUID programId;

    /** Required when {@code subProgramId} is null (legacy). */
    private UUID anchorId;

    /** When set, {@code programId} / {@code anchorId} follow the sub-program. */
    private UUID subProgramId;

    /** Borrower envelope on {@code sub_program_borrowers} when {@code subProgramId} is set. Required then. */
    private BigDecimal subProgramBorrowerLimit;

    /** Defaults e.g. ACTIVE on server */
    private String status;
}
