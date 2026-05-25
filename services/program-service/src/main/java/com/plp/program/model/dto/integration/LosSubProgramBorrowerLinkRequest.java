package com.plp.program.model.dto.integration;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class LosSubProgramBorrowerLinkRequest extends LosIntegrationBaseRequest {

    @NotNull
    private UUID subProgramId;

    @NotNull
    private UUID borrowerId;

    @NotNull
    @DecimalMin(value = "0.01", inclusive = true)
    private BigDecimal borrowerLimit;

    @DecimalMin(value = "0.00", inclusive = true)
    private BigDecimal utilizedLimit;

    private BigDecimal availableLimit;
}
