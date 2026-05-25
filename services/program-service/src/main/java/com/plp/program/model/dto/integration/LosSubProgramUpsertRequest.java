package com.plp.program.model.dto.integration;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class LosSubProgramUpsertRequest extends LosIntegrationBaseRequest {

    @Size(max = 100)
    private String losSubProgramId;

    private UUID plpProgramId;

    @Size(max = 30)
    private String programCode;

    @NotNull
    private UUID anchorId;

    @NotNull
    private UUID lenderId;

    @NotBlank
    @Size(max = 50)
    private String subProgramCode;

    @NotBlank
    @Size(max = 255)
    private String name;

    @NotBlank
    @Size(max = 40)
    private String flowType;

    @NotBlank
    @Size(max = 20)
    private String anchorRole;

    @NotBlank
    @Size(max = 20)
    private String borrowerRole;

    @NotNull
    @DecimalMin(value = "0.01", inclusive = true)
    private BigDecimal subProgramLimit;

    private BigDecimal interestRate;

    private Integer maxTenureDays;

    @AssertTrue(message = "Exactly one of plpProgramId or programCode must be provided")
    public boolean isProgramReferenceValid() {
        boolean hasId = plpProgramId != null;
        boolean hasCode = programCode != null && !programCode.isBlank();
        return hasId != hasCode;
    }
}
