package com.plp.program.model.dto.integration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class LosBorrowerUpsertRequest extends LosIntegrationBaseRequest {

    @NotBlank
    @Size(max = 100)
    private String losBorrowerId;

    @NotNull
    private UUID plpProgramId;

    @NotNull
    private UUID plpAnchorId;

    @NotNull
    private LosBorrowerPayload borrower;

    @Data
    public static class LosBorrowerPayload {

        @NotBlank
        @Size(max = 200)
        private String name;

        @Size(max = 100)
        private String email;

        @Size(max = 15)
        private String phone;

        @Size(max = 10)
        private String pan;

        @Size(max = 15)
        private String gstin;
    }
}
