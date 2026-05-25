package com.plp.iam.model.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LosProvisionUserResponse {
    private String userId;
    private String email;
    private String role;
    private boolean created;
}
