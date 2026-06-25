package com.plp.program.model.dto;

import com.plp.program.model.enums.ProductType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class ProductRepaymentDefaultDto {

    private ProductType productType;
    private String repaymentMechanism;
    private String pgProviderCode;
    private Boolean enabled;
    private Instant updatedAt;
    private String updatedBy;
}
