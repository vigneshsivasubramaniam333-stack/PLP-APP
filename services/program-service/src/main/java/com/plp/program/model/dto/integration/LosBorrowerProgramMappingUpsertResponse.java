package com.plp.program.model.dto.integration;

import com.plp.program.model.enums.BorrowerProgramMappingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LosBorrowerProgramMappingUpsertResponse {

    private UUID plpBorrowerProgramMappingId;
    private BorrowerProgramMappingStatus mappingStatus;
    private boolean created;
    private Boolean updated;
}
