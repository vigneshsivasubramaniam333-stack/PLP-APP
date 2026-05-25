package com.plp.iam.controller;

import com.plp.iam.model.dto.AuthResponse;
import com.plp.iam.model.dto.CreateUserRequest;
import com.plp.iam.model.dto.LoginRequest;
import com.plp.iam.model.entity.User;
import com.plp.iam.model.enums.UserRole;
import com.plp.iam.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** Only {@link UserRole#BORROWER} self-registers without privileged caller; other roles need Platform Admin or lender provision rules below. */
    private static final Set<UserRole> SELF_REGISTRABLE_ROLES = Set.of(UserRole.BORROWER);

    /** Anchor-linked roles that lender operations may provision ({@link AuthService#createUser}). */
    private static final Set<UserRole> ANCHOR_PORTAL_REGISTRABLE_ROLES =
            Set.of(UserRole.ANCHOR_ADMIN, UserRole.ANCHOR_MAKER, UserRole.ANCHOR_CHECKER);

    private static boolean isPlatformAdmin(String callerRolesHeader) {
        return callerRolesHeader != null && callerRolesHeader.contains("PLATFORM_ADMIN");
    }

    /**
     * Credit Analyst / Manager may register anchor-portal tenant users while onboarding anchors from the lender UI.
     * Header format matches program-service ({@code X-User-Roles}, comma-separated legacy roles allowed).
     */
    private static boolean lenderMayProvisionAnchorUsers(String callerRolesHeader) {
        if (callerRolesHeader == null || callerRolesHeader.isBlank()) {
            return false;
        }
        String upper = callerRolesHeader.toUpperCase(Locale.ROOT);
        return upper.contains("CREDIT_ANALYST")
                || upper.contains("CREDIT_MANAGER")
                || upper.contains("PROGRAM_MANAGER");
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            AuthResponse response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(
            @Valid @RequestBody CreateUserRequest request,
            @RequestHeader(value = "X-User-Roles", required = false) String callerRoles) {
        if (!SELF_REGISTRABLE_ROLES.contains(request.getRole())) {
            boolean allowed =
                    isPlatformAdmin(callerRoles)
                            || (ANCHOR_PORTAL_REGISTRABLE_ROLES.contains(request.getRole())
                                    && lenderMayProvisionAnchorUsers(callerRoles));
            if (!allowed) {
                throw new RuntimeException("Insufficient privilege to assign role: " + request.getRole());
            }
        }
        User user = authService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "status", "SUCCESS",
                "data", Map.of(
                        "userId", user.getId().toString(),
                        "email", user.getEmail(),
                        "role", user.getRole().name()
                )
        ));
    }
}
