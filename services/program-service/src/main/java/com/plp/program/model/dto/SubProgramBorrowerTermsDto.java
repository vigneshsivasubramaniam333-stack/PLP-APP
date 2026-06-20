package com.plp.program.model.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SubProgramBorrowerTermsDto {

    private BigDecimal borrowerLimit;
    private BigDecimal interestRate;
    private BigDecimal discountMarginPercent;
    private Integer creditPeriodDays;
    private String discountHold;
    private String paymentMethod;
    private BigDecimal overdueInterestRate;
}
