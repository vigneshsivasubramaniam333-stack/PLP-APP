package com.plp.program.model.dto;

import lombok.Data;

import java.util.Map;

@Data
public class ProgramEditDto {

    /** Updates {@link com.plp.program.model.entity.Program#setProgramName} when non-null. */
    private String name;

    private String description;

    /** Partial eligibility-related entries merged into {@code Program.config}; null values skipped. */
    private Map<String, Object> config;
}
