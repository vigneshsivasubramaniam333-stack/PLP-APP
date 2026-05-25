package com.plp.iam.model.dto;

import com.plp.iam.model.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private long expiresIn;
    private String userId;
    private String email;
    private String fullName;
    private UserRole role;
    /** Borrower UUID, anchor UUID, etc. — aligns with {@link com.plp.iam.model.entity.User#getLinkedEntityId()}. */
    private String linkedEntityId;
    /** e.g. BORROWER, ANCHOR — aligns with {@link com.plp.iam.model.entity.User#getLinkedEntityType()}. */
    private String linkedEntityType;
}
