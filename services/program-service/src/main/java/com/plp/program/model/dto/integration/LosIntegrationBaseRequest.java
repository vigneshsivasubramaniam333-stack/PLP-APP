package com.plp.program.model.dto.integration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/** Shared LOS envelope: tenant / originating system key for idempotency and audit. */
@Getter
@Setter
public abstract class LosIntegrationBaseRequest implements Serializable {

    @NotBlank
    @Size(max = 50)
    private String sourceSystem;
}
