package com.plp.program.model.dto.integration;

import com.plp.program.model.enums.ProductType;
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
public class LosProgramUpsertRequest extends LosIntegrationBaseRequest {

    @Size(max = 100)
    private String losProgramId;

    @NotBlank
    @Size(max = 30)
    private String programCode;

    @NotBlank
    @Size(max = 200)
    private String programName;

    @NotNull
    private ProductType productType;

    @NotNull
    private UUID lenderId;

    @NotNull
    @DecimalMin(value = "0.01", inclusive = true)
    private BigDecimal programLimit;

    @NotNull
    @DecimalMin(value = "0.01", inclusive = true)
    private BigDecimal maxBorrowerLimit;

    private UUID anchorId;

    @DecimalMin(value = "0.00", inclusive = true)
    private BigDecimal anchorLimit;

    private BigDecimal defaultInterestRate;

    private Integer maxTenureDays;

    private LocalDate validFrom;

    private LocalDate validTo;

    /** YES | NO — Encore LMS for invoice loans (bl-core lms_entry_in). */
    @Size(max = 3)
    private String lmsEntryIn;

    @Size(max = 50)
    private String encoreProductCode;
}
