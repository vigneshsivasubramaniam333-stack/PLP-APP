package com.plp.iam.controller;

import com.plp.iam.model.dto.UserDto;
import com.plp.iam.model.enums.UserRole;
import com.plp.iam.security.JwtTokenProvider;
import com.plp.iam.service.UserQueryService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserQueryService userQueryService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> listUsers(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String linkedEntityType) {

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
                    .body(Map.of("status", "ERROR", "message", "Only PLATFORM_ADMIN can list users"));
        }

        try {
            List<UserDto> users = userQueryService.listUsers(role, linkedEntityType);
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", users));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }
}
