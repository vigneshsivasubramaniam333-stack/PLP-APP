package com.plp.program.model.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class ProgramEditDto {

    /** Updates {@link com.plp.program.model.entity.Program#setProgramName} when non-null. */
    private String name;

    private String description;

    /** When non-null, updates the program-level margin applied to invoice eligible amounts. */
    private BigDecimal marginPercent;

    /** When non-null, updates the max dealer / borrower limit for this program (must be &gt; 0). */
    private BigDecimal maxBorrowerLimit;

    /** Partial eligibility-related entries merged into {@code Program.config}; null values skipped. */
    private Map<String, Object> config;

    /** Operational toggles merged into {@code Program.parameters}. */
    private Map<String, Object> parameters;

    /** YES / NO — post disbursed invoice loans to Encore LMS. */
    private String lmsEntryIn;

    /** Encore product code when {@code lmsEntryIn=YES}. */
    private String encoreProductCode;
}
