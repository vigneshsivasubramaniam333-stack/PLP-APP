package com.plp.iam.model.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.plp.iam.jackson.UserRoleJsonDeserializer;
import com.plp.iam.model.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateUserRequest {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 8, max = 100)
    private String password;

    @NotBlank
    private String fullName;

    private String phone;

    @NotNull
    @JsonDeserialize(using = UserRoleJsonDeserializer.class)
    private UserRole role;

    private UUID linkedEntityId;

    private String linkedEntityType;
}
