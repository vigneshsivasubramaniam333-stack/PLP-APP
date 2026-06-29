package com.plp.program.model.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class EarlyPayCreateParameterRequest {
    private UUID subProgramId;
    private LocalDate epDate;
    private BigDecimal discountPercentage;
    private BigDecimal epAmount;
    private BigDecimal totalMarginAmount;
}
