package com.plp.program.model.dto.integration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class LosSubProgramActivateRequest extends LosIntegrationBaseRequest {

    @NotBlank
    @Size(max = 100)
    private String losSubProgramId;
}
