package com.plp.program.model.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class EffectiveBorrowerTermsDto {

    private UUID subProgramId;
    private UUID borrowerId;
    private UUID programId;

    private BigDecimal interestRate;
    private BigDecimal discountMarginPercent;
    private Integer creditPeriodDays;
    private String discountHold;
    private String paymentMethod;
    private BigDecimal overdueInterestRate;

    private BigDecimal borrowerLimit;
    private BigDecimal subProgramInterestRate;
    private Integer subProgramMaxTenureDays;
    private BigDecimal subProgramMarginPercent;
}
