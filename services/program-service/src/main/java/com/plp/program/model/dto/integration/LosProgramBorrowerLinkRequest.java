package com.plp.program.model.dto.integration;

import com.plp.program.model.enums.ProductType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class LosProgramBorrowerLinkRequest {

    @NotBlank
    @Size(max = 50)
    private String sourceSystem;

    @NotBlank
    @Size(max = 100)
    private String losApplicationId;

    @NotBlank
    @Size(max = 100)
    private String losBorrowerId;

    @Valid
    @NotNull
    private LosBorrowerDetailsDto borrower;

    @Valid
    @NotNull
    private LosProgramDetailsDto program;

    @Valid
    @NotNull
    private LosSubProgramDetailsDto subProgram;

    @NotNull
    @DecimalMin(value = "0.01", inclusive = true)
    private BigDecimal approvedLimit;

    @NotNull
    private LocalDate validFrom;

    @NotNull
    private LocalDate validTo;

    @Data
    public static class LosBorrowerDetailsDto {

        @NotBlank
        @Size(max = 200)
        private String name;

        @Size(max = 100)
        private String email;

        @Size(max = 15)
        private String phone;

        @Size(max = 10)
        private String pan;
    }

    @Data
    public static class LosProgramDetailsDto {

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
    }

    @Data
    public static class LosSubProgramDetailsDto {

        @NotBlank
        @Size(max = 50)
        private String code;

        @NotBlank
        @Size(max = 255)
        private String name;

        @NotNull
        private UUID anchorId;

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
    }
}
