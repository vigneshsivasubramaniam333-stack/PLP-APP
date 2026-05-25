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
public class LosAnchorSyncResponse {

    private UUID plpAnchorId;
    private String anchorCode;
    private boolean created;
    /** True when an existing anchor was patched; omitted or false on pure create. */
    private Boolean updated;
}
