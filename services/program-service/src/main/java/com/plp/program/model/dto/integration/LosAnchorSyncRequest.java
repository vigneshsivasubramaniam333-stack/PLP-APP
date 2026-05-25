package com.plp.program.model.dto.integration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LosAnchorSyncRequest {

    @NotBlank
    @Size(max = 50)
    private String sourceSystem;

    @NotBlank
    @Size(max = 100)
    private String losAnchorId;

    @Valid
    @NotNull
    private LosAnchorPayload anchor;

    @Data
    public static class LosAnchorPayload {

        @NotBlank
        @Size(max = 200)
        private String name;

        @NotBlank
        @Size(max = 20)
        private String code;

        @Size(max = 10)
        private String pan;

        @Size(max = 15)
        private String gstin;

        @Size(max = 100)
        private String email;

        @Size(max = 15)
        private String mobile;

        private String address;
    }
}
