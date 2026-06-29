package com.plp.program.model.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SubProgramEditDto {

    private String name;

    private BigDecimal interestRate;

    private BigDecimal marginPercent;

    private Integer maxTenureDays;

    /** Allowed when sub-program is in DRAFT status. */
    private BigDecimal subProgramLimit;

    /** YES/NO — SBD Early Pay (only for SALES_BILL_DISCOUNTING sub-programs). */
    private String allowEarlyPay;
}
