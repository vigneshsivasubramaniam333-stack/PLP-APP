package com.plp.program.model.dto.integration;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class LosApplicationCleanupRequest {

    private String sourceSystem = "LOS";

    @NotBlank
    private String losApplicationId;

    private UUID plpBorrowerId;
    private UUID plpSubProgramBorrowerId;
    private UUID plpBorrowerProgramMappingId;
    private boolean deleteBorrowerRecord = true;
}
