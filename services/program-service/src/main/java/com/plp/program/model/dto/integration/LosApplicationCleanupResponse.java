package com.plp.program.model.dto.integration;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LosApplicationCleanupResponse {

    private String summary;
    private int loansRemoved;
    private int invoicesRemoved;
    private boolean borrowerRemoved;
}
