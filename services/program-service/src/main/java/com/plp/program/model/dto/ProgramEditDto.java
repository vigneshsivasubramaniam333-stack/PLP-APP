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

    /** Partial eligibility-related entries merged into {@code Program.config}; null values skipped. */
    private Map<String, Object> config;
}
