package com.plp.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    @Value("${jwt.secret}")
    private String jwtSecret;

    private static final List<String> OPEN_ENDPOINTS = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh",
            "/actuator/health",
            "/eureka"
    );

    private static final List<String> INTERNAL_HEADERS = List.of(
            "X-User-Id",
            "X-User-Roles",
            "X-User-Email",
            "X-Linked-Entity-Id",
            "X-Linked-Entity-Type"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // Strip client-supplied internal headers to prevent header injection
        ServerHttpRequest.Builder sanitized = exchange.getRequest().mutate();
        INTERNAL_HEADERS.forEach(h -> sanitized.headers(headers -> headers.remove(h)));
        ServerWebExchange sanitizedExchange = exchange.mutate().request(sanitized.build()).build();

        String authHeader = sanitizedExchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        boolean isOpen = isOpenEndpoint(path);

        if (isOpen && (authHeader == null || !authHeader.startsWith("Bearer "))) {
            return chain.filter(sanitizedExchange);
        }

        if (!isOpen && (authHeader == null || !authHeader.startsWith("Bearer "))) {
            sanitizedExchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return sanitizedExchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            ServerHttpRequest.Builder requestBuilder = sanitizedExchange.getRequest().mutate()
                    .header("X-User-Id", claims.getSubject())
                    .header("X-User-Roles", claims.get("roles", String.class))
                    .header("X-User-Email", claims.get("email", String.class));
            String linkedEntityId = claims.get("linkedEntityId", String.class);
            String linkedEntityType = claims.get("linkedEntityType", String.class);
            if (linkedEntityId != null) {
                requestBuilder.header("X-Linked-Entity-Id", linkedEntityId);
            }
            if (linkedEntityType != null) {
                requestBuilder.header("X-Linked-Entity-Type", linkedEntityType);
            }
            ServerHttpRequest mutatedRequest = requestBuilder.build();

            return chain.filter(sanitizedExchange.mutate().request(mutatedRequest).build());
        } catch (Exception e) {
            if (isOpen) {
                return chain.filter(sanitizedExchange);
            }
            sanitizedExchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return sanitizedExchange.getResponse().setComplete();
        }
    }

    private boolean isOpenEndpoint(String path) {
        return OPEN_ENDPOINTS.stream().anyMatch(path::startsWith);
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
