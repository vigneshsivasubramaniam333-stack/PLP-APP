package com.plp.lending.model.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class LoanRequestDTO {
    private UUID borrowerId;
    private UUID programId;
    private UUID anchorId;
    private String productType;
    private BigDecimal requestedAmount;
    private BigDecimal interestRate;
    private Integer tenureDays;
    private BigDecimal processingFee;
    private UUID invoiceId;
    private UUID salaryDataId;
}
