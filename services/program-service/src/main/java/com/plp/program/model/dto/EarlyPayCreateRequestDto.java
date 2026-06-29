package com.plp.program.model.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class EarlyPayCreateRequestDto {
    private UUID invoiceId;
    private UUID epParameterId;
    private BigDecimal requestedAmount;
}
