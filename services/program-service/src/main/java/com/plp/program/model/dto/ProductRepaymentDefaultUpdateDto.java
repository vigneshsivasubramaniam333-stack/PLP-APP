package com.plp.program.model.dto;

import lombok.Data;

@Data
public class ProductRepaymentDefaultUpdateDto {

    private String repaymentMechanism;
    private String pgProviderCode;
    private Boolean enabled;
}
