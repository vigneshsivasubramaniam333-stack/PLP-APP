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
public class LosSubProgramBorrowerLinkResponse {

    private UUID plpSubProgramBorrowerId;
    private String status;
    private boolean created;
    private Boolean updated;
}
