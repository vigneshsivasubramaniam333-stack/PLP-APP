package com.plp.iam.model.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.plp.iam.jackson.UserRoleJsonDeserializer;
import com.plp.iam.model.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class LosProvisionUserRequest {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String fullName;

    private String phone;

    @NotNull
    @JsonDeserialize(using = UserRoleJsonDeserializer.class)
    private UserRole role;

    @NotNull
    private UUID linkedEntityId;

    @NotBlank
    private String linkedEntityType;
}
