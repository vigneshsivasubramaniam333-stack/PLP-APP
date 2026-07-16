package com.plp.program.model.dto.integration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
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

    /** Commercial fields so LOS can mirror L1/L2 edits back to RM. */
    private BigDecimal defaultInterestRate;
    private BigDecimal programLimit;
    private BigDecimal maxBorrowerLimit;
    private Integer maxTenureDays;
    private BigDecimal dependencyVintagePercent;
    private Integer anchorRelationshipVintageMonths;
    private String lmsEntryIn;
    private String encoreProductCode;
}
