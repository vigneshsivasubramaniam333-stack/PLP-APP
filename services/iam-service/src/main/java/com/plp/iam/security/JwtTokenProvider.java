package com.plp.iam.security;

import com.plp.iam.model.entity.User;
import com.plp.iam.model.enums.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.access-token-expiry-ms:28800000}")
    private long accessTokenExpiryMs;

    @Value("${jwt.refresh-token-expiry-ms:86400000}")
    private long refreshTokenExpiryMs;

    public String generateAccessToken(User user) {
        return generateAccessToken(user, user.getRole());
    }

    /**
     * @param roleClaim role name stored in JWT ({@code roles} claim); may differ from {@link User#getRole()}
     *                  during legacy DB compatibility windows.
     */
    public String generateAccessToken(User user, UserRole roleClaim) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpiryMs);

        UserRole claimsRole = roleClaim != null ? roleClaim : user.getRole();
        var builder = Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("roles", claimsRole.name())
                .claim("name", user.getFullName())
                .issuedAt(now)
                .expiration(expiry);
        if (user.getLinkedEntityId() != null) {
            builder.claim("linkedEntityId", user.getLinkedEntityId().toString());
        }
        if (user.getLinkedEntityType() != null && !user.getLinkedEntityType().isBlank()) {
            builder.claim("linkedEntityType", user.getLinkedEntityType());
        }
        return builder.signWith(key).compact();
    }

    public String generateRefreshToken(User user) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        Date expiry = new Date(now.getTime() + refreshTokenExpiryMs);

        return Jwts.builder()
                .subject(user.getId().toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public Claims validateToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
