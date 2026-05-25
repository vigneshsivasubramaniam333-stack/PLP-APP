package com.plp.iam.model.dto;

import com.plp.iam.model.entity.User;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class UserDto {
    String id;
    String email;
    String fullName;
    String role;
    String linkedEntityId;
    String linkedEntityType;
    String status;

    public static UserDto fromEntity(User u) {
        return UserDto.builder()
                .id(u.getId().toString())
                .email(u.getEmail())
                .fullName(u.getFullName())
                .role(u.getRole().name())
                .linkedEntityId(u.getLinkedEntityId() != null ? u.getLinkedEntityId().toString() : null)
                .linkedEntityType(u.getLinkedEntityType())
                .status(u.getStatus() != null ? u.getStatus().name() : null)
                .build();
    }
}
