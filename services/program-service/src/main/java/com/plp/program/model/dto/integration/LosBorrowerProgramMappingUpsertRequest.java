package com.plp.program.model.dto.integration;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class LosBorrowerProgramMappingUpsertRequest extends LosIntegrationBaseRequest {

    @NotBlank
    @Size(max = 100)
    private String losApplicationId;

    @NotBlank
    @Size(max = 100)
    private String losBorrowerId;

    @NotNull
    private UUID plpBorrowerId;

    @NotNull
    private UUID plpProgramId;

    @NotNull
    private UUID plpSubProgramId;

    @NotNull
    @DecimalMin(value = "0.01", inclusive = true)
    private BigDecimal approvedLimit;

    @NotNull
    private LocalDate validFrom;

    @NotNull
    private LocalDate validTo;
}
