package com.plp.report.model.dto;

import lombok.*;
import java.math.BigDecimal;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class PortfolioSummary {
    private String programCode;
    private String programName;
    private String productType;
    private long totalLoans;
    private long activeLoans;
    private long overdueLoans;
    private BigDecimal totalDisbursed;
    private BigDecimal totalOutstanding;
    private BigDecimal totalOverdue;
    private double npaPercent;
}
