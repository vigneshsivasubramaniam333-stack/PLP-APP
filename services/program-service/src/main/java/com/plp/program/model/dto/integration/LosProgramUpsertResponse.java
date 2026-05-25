package com.plp.program.model.dto.integration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LosProgramUpsertResponse {

    private UUID plpProgramId;
    private String programCode;
    private boolean created;
    /** Present when an existing program row was updated. */
    private Boolean updated;
}
