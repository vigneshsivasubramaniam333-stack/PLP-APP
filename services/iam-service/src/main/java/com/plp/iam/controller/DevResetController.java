package com.plp.iam.controller;

import com.plp.iam.dev.DevResetGuard;
import com.plp.iam.dev.DevUserResetService;
import com.plp.iam.model.enums.UserRole;
import com.plp.iam.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/dev")
@RequiredArgsConstructor
public class DevResetController {

    private final DevResetGuard devResetGuard;
    private final DevUserResetService devUserResetService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/reset-users")
    public ResponseEntity<Map<String, String>> resetUsers(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        devResetGuard.requireDevResetEnabled();

        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", "ERROR", "message", "Missing or invalid Authorization header"));
        }

        Claims claims;
        try {
            claims = jwtTokenProvider.validateToken(authorization.substring(7));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", "ERROR", "message", "Invalid or expired token"));
        }

        String callerRole = claims.get("roles", String.class);
        if (!UserRole.PLATFORM_ADMIN.name().equals(callerRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("status", "ERROR", "message", "Only PLATFORM_ADMIN can run dev reset"));
        }

        devUserResetService.deleteAllUsersExceptPlatformAdmin();

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Demo data reset completed"));
    }
}
