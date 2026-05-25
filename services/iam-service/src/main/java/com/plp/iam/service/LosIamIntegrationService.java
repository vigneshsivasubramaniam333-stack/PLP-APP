package com.plp.iam.service;

import com.plp.iam.model.dto.LosProvisionUserRequest;
import com.plp.iam.model.dto.LosProvisionUserResponse;
import com.plp.iam.model.entity.User;
import com.plp.iam.model.enums.UserRole;
import com.plp.iam.model.enums.UserStatus;
import com.plp.iam.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LosIamIntegrationService {

    private static final Set<UserRole> ANCHOR_LINKED_ROLES =
            Set.of(UserRole.ANCHOR_ADMIN, UserRole.ANCHOR_MAKER, UserRole.ANCHOR_CHECKER);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${plp.los-integration.default-user-password:ChangeMe@PLP2026}")
    private String defaultUserPassword;

    @Transactional
    public LosProvisionUserResponse provisionUser(LosProvisionUserRequest request) {
        validateLinkedEntity(request);

        String email = request.getEmail().trim();
        Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            User user = existing.get();
            linkExistingUserIfNeeded(user, request);
            log.info(
                    "LOS IAM provision skipped — user already exists: email={} role={} linkedEntityId={}",
                    user.getEmail(),
                    user.getRole(),
                    user.getLinkedEntityId());
            return LosProvisionUserResponse.builder()
                    .userId(user.getId().toString())
                    .email(user.getEmail())
                    .role(user.getRole().name())
                    .created(false)
                    .build();
        }

        UserRole role = request.getRole() != null ? request.getRole() : defaultRoleForEntity(request.getLinkedEntityType());
        User user =
                User.builder()
                        .email(email)
                        .passwordHash(passwordEncoder.encode(defaultUserPassword))
                        .fullName(request.getFullName().trim())
                        .phone(trimOrNull(request.getPhone()))
                        .role(role)
                        .linkedEntityId(request.getLinkedEntityId())
                        .linkedEntityType(request.getLinkedEntityType().trim().toUpperCase())
                        .status(UserStatus.ACTIVE)
                        .build();
        user = userRepository.save(user);
        log.info(
                "LOS IAM user provisioned: email={} role={} linkedEntityType={} linkedEntityId={}",
                user.getEmail(),
                user.getRole(),
                user.getLinkedEntityType(),
                user.getLinkedEntityId());
        return LosProvisionUserResponse.builder()
                .userId(user.getId().toString())
                .email(user.getEmail())
                .role(user.getRole().name())
                .created(true)
                .build();
    }

    private static UserRole defaultRoleForEntity(String linkedEntityType) {
        if ("ANCHOR".equalsIgnoreCase(linkedEntityType)) {
            return UserRole.ANCHOR_ADMIN;
        }
        if ("BORROWER".equalsIgnoreCase(linkedEntityType)) {
            return UserRole.BORROWER;
        }
        throw new RuntimeException("Unsupported linkedEntityType for LOS user provision: " + linkedEntityType);
    }

    private static void validateLinkedEntity(LosProvisionUserRequest request) {
        String type = request.getLinkedEntityType() == null ? "" : request.getLinkedEntityType().trim().toUpperCase();
        if ("ANCHOR".equals(type)) {
            if (request.getRole() != null && !ANCHOR_LINKED_ROLES.contains(request.getRole())) {
                throw new RuntimeException("Anchor portal users must use ANCHOR_ADMIN, ANCHOR_MAKER, or ANCHOR_CHECKER");
            }
            return;
        }
        if ("BORROWER".equals(type)) {
            if (request.getRole() != null && request.getRole() != UserRole.BORROWER) {
                throw new RuntimeException("Borrower portal users must use BORROWER role");
            }
            return;
        }
        throw new RuntimeException("linkedEntityType must be ANCHOR or BORROWER");
    }

    private void linkExistingUserIfNeeded(User user, LosProvisionUserRequest request) {
        UUID expected = request.getLinkedEntityId();
        String expectedType = request.getLinkedEntityType().trim().toUpperCase();
        if (user.getLinkedEntityId() != null
                && expected != null
                && !user.getLinkedEntityId().equals(expected)) {
            throw new RuntimeException(
                    "Email already registered to a different "
                            + user.getLinkedEntityType()
                            + " entity");
        }
        if (user.getLinkedEntityType() != null
                && !user.getLinkedEntityType().equalsIgnoreCase(expectedType)) {
            throw new RuntimeException("Email already registered with a different linked entity type");
        }
        if (user.getLinkedEntityId() == null && expected != null) {
            user.setLinkedEntityId(expected);
            user.setLinkedEntityType(expectedType);
            userRepository.save(user);
        }
    }

    private static String trimOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
