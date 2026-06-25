package com.plp.iam.service;

import com.plp.iam.model.converter.UserRoleLegacyMapping;
import com.plp.iam.model.dto.AuthResponse;
import com.plp.iam.model.dto.ChangePasswordRequest;
import com.plp.iam.model.dto.CreateUserRequest;
import com.plp.iam.model.dto.LoginRequest;
import com.plp.iam.model.entity.User;
import com.plp.iam.model.enums.UserRole;
import com.plp.iam.model.enums.UserStatus;
import com.plp.iam.repository.UserRepository;
import com.plp.iam.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /**
     * Tenant-scoped anchor portal roles: each user is bound to one anchor ({@link User#getLinkedEntityId()})
     * with {@code linkedEntityType == "ANCHOR"}. Supports maker-checker and admin workflows per tenant.
     */
    private static final Set<UserRole> ANCHOR_LINKED_ROLES = Set.of(
            UserRole.ANCHOR_ADMIN,
            UserRole.ANCHOR_MAKER,
            UserRole.ANCHOR_CHECKER
    );

    /** Default password for admin-provisioned lender/anchor users (not borrower self-registration). */
    public static final String DEFAULT_PROVISIONED_TEMPORARY_PASSWORD = "Temp@123";

    private static final Pattern STRONG_PASSWORD = Pattern.compile(
            "^(?=.*[0-9])(?=.*[^A-Za-z0-9]).{8,}$");

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid credentials");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new RuntimeException("Account is not active. Status: " + user.getStatus());
        }

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        // Temporary compatibility: DB may still store PROGRAM_MANAGER / TREASURY until migrated.
        UserRole effectiveRole = UserRoleLegacyMapping.fromLegacyDbString(userRepository.findRoleRawById(user.getId()));
        if (effectiveRole == null) {
            effectiveRole = user.getRole();
        }

        log.info("User logged in: {} ({})", user.getEmail(), effectiveRole);

        return buildAuthResponse(user, effectiveRole);
    }

    @Transactional
    public AuthResponse changePassword(UUID userId, ChangePasswordRequest request) {
        if (userId == null) {
            throw new RuntimeException("Sign-in is required");
        }
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("Passwords do not match");
        }
        if (!STRONG_PASSWORD.matcher(request.getNewPassword()).matches()) {
            throw new RuntimeException(
                    "Password must be at least 8 characters and include a number and a special character");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Invalid session"));
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordResetRequired(false);
        user = userRepository.save(user);

        UserRole effectiveRole = UserRoleLegacyMapping.fromLegacyDbString(userRepository.findRoleRawById(user.getId()));
        if (effectiveRole == null) {
            effectiveRole = user.getRole();
        }
        log.info("Password changed for user: {} ({})", user.getEmail(), effectiveRole);
        return buildAuthResponse(user, effectiveRole);
    }

    private AuthResponse buildAuthResponse(User user, UserRole effectiveRole) {
        String accessToken = jwtTokenProvider.generateAccessToken(user, effectiveRole);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(28800)
                .userId(user.getId().toString())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(effectiveRole)
                .linkedEntityId(user.getLinkedEntityId() != null ? user.getLinkedEntityId().toString() : null)
                .linkedEntityType(user.getLinkedEntityType())
                .passwordResetRequired(user.isPasswordResetRequired())
                .build();
    }

    @Transactional
    public User createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered: " + request.getEmail());
        }

        // ANCHOR_ADMIN / ANCHOR_MAKER / ANCHOR_CHECKER are tenant-scoped anchor users.
        if (ANCHOR_LINKED_ROLES.contains(request.getRole())) {
            if (request.getLinkedEntityId() == null
                    || request.getLinkedEntityType() == null
                    || !"ANCHOR".equals(request.getLinkedEntityType())) {
                throw new RuntimeException("Anchor users must be linked to an anchor");
            }
        }

        String passwordToStore = request.getRole() == UserRole.BORROWER
                ? request.getPassword()
                : DEFAULT_PROVISIONED_TEMPORARY_PASSWORD;
        boolean passwordResetRequired = request.getRole() != UserRole.BORROWER;

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(passwordToStore))
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .role(request.getRole())
                .linkedEntityId(request.getLinkedEntityId())
                .linkedEntityType(request.getLinkedEntityType())
                .status(UserStatus.ACTIVE)
                .passwordResetRequired(passwordResetRequired)
                .build();

        user = userRepository.save(user);
        log.info("User created: {} ({}) with role {}", user.getEmail(), user.getId(), user.getRole());
        return user;
    }
}
