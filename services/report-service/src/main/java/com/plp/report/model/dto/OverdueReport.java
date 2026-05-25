package com.plp.report.model.dto;

import lombok.*;
import java.math.BigDecimal;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class OverdueReport {
    private String loanNumber;
    private String borrowerName;
    private String programCode;
    private String productType;
    private BigDecimal outstandingAmount;
    private int dpd;
    private String dpdBucket;
    private String dueDate;
}
