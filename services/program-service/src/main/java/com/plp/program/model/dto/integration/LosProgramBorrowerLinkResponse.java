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
public class LosProgramBorrowerLinkResponse {

    private UUID plpBorrowerId;
    private UUID plpProgramId;
    private UUID plpSubProgramId;
    private UUID plpBorrowerProgramMappingId;
    private BorrowerProgramMappingStatus mappingStatus;
    /** True when a new borrower-program mapping row was created in this aggregate call. */
    private boolean created;
    /** Present when mapping row was unchanged (false) or updated (true) where applicable. */
    private Boolean updated;
}
