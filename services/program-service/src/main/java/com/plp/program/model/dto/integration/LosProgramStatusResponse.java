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
public class LosProgramStatusResponse {

    private UUID plpProgramId;
    private String programCode;
    private String status;
    /** Present when L1/L2 send-back recorded remarks on the program. */
    private String approvalRemarks;
}
