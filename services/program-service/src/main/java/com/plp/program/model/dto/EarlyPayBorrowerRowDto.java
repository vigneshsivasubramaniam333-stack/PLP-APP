package com.plp.program.model.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class EarlyPayBorrowerRowDto {
    private UUID membershipId;
    private UUID borrowerId;
    private String borrowerName;
    private String enableEarlyPay;
}
