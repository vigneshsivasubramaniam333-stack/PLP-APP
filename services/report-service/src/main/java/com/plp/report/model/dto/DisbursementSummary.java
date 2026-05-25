package com.plp.report.model.dto;

import lombok.*;
import java.math.BigDecimal;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class DisbursementSummary {
    private String date;
    private String productType;
    private long loanCount;
    private BigDecimal totalDisbursed;
    private BigDecimal totalApproved;
}
