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
    private String paymentMethodMode;
    private BigDecimal overdueInterestRate;
    private String partyCode;

    private String borrowerOdAccountNumber;
    private String borrowerOdBankName;
    private String borrowerOdBankIfsc;
    private String borrowerOdAccountName;

    private String idfcCollectionAccountName;
    private String idfcOdAccountNumber;
    private String idfcIfscCode;
    private String idfcUpiId;

    private String castlerEscrowAccountIn;
    private String castlerEscrowAccountId;
    private String castlerEscrowPayeeId;

    private String razorpayRouteAccountId;
    private String razorpaySmartCollectAcId;
    private BigDecimal razorpayFee;

    private String hdfcAccountNo;
    private String hdfcIfscCode;
}
