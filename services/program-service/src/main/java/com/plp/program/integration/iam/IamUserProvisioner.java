package com.plp.program.integration.iam;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class IamUserProvisioner {

    public static final String INTEGRATION_API_KEY_HEADER = "X-Los-Integration-Key";

    private final RestTemplateBuilder restTemplateBuilder;

    @Value("${plp.iam.base-url:http://localhost:8081}")
    private String iamBaseUrl;

    @Value("${plp.los-integration.api-key:}")
    private String integrationApiKey;

    public ProvisionResult provisionAnchorAdmin(UUID plpAnchorId, String email, String fullName, String phone) {
        return provisionPortalUser(plpAnchorId, "ANCHOR", "ANCHOR_ADMIN", email, fullName, phone, true);
    }

    public void provisionBorrowerUser(UUID plpBorrowerId, String email, String fullName, String phone) {
        provisionPortalUser(plpBorrowerId, "BORROWER", "BORROWER", email, fullName, phone, false);
    }

    private ProvisionResult provisionPortalUser(
            UUID linkedEntityId,
            String linkedEntityType,
            String role,
            String email,
            String fullName,
            String phone,
            boolean generateTemporaryPassword) {
        if (email == null || email.isBlank()) {
            log.info(
                    "Skipping PLP portal user provision — no email for {} {}",
                    linkedEntityType,
                    linkedEntityId);
            return ProvisionResult.empty();
        }
        String normalizedEmail = email.trim();
        if (!normalizedEmail.contains("@") || normalizedEmail.indexOf('@') == 0
                || normalizedEmail.indexOf('@') == normalizedEmail.length() - 1
                || normalizedEmail.indexOf('.', normalizedEmail.indexOf('@')) <= normalizedEmail.indexOf('@')) {
            log.error(
                    "Skipping PLP portal user provision — invalid email for {} {} email={}",
                    linkedEntityType,
                    linkedEntityId,
                    normalizedEmail);
            return ProvisionResult.empty();
        }
        if (integrationApiKey == null || integrationApiKey.isBlank()) {
            log.warn(
                    "Skipping PLP portal user provision — plp.los-integration.api-key is not configured ({}/{})",
                    linkedEntityType,
                    linkedEntityId);
            return ProvisionResult.empty();
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", normalizedEmail);
        body.put("fullName", fullName != null && !fullName.isBlank() ? fullName.trim() : normalizedEmail);
        body.put("phone", phone);
        body.put("role", role);
        body.put("linkedEntityId", linkedEntityId);
        body.put("linkedEntityType", linkedEntityType);
        if (generateTemporaryPassword) {
            body.put("generateTemporaryPassword", true);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(INTEGRATION_API_KEY_HEADER, integrationApiKey);

        try {
            RestTemplate restTemplate = restTemplateBuilder.build();
            ResponseEntity<Map<String, Object>> response =
                    restTemplate.exchange(
                            iamBaseUrl + "/api/v1/integrations/los/users",
                            HttpMethod.POST,
                            new HttpEntity<>(body, headers),
                            new ParameterizedTypeReference<>() {});
            log.info(
                    "PLP portal user provision response for {} {}: status={}",
                    linkedEntityType,
                    linkedEntityId,
                    response.getStatusCode());
            Map<String, Object> data = unwrapData(response.getBody());
            if (data == null) {
                return ProvisionResult.empty();
            }
            return ProvisionResult.builder()
                    .userId(asString(data.get("userId")))
                    .temporaryPassword(asString(data.get("temporaryPassword")))
                    .passwordResetRequired(asBoolean(data.get("passwordResetRequired")))
                    .created(Boolean.TRUE.equals(asBoolean(data.get("created"))))
                    .build();
        } catch (RestClientException e) {
            log.error(
                    "PLP portal user provision failed for {} {} email={}: {}",
                    linkedEntityType,
                    linkedEntityId,
                    email,
                    e.getMessage());
            return ProvisionResult.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> unwrapData(Map<String, Object> body) {
        if (body == null) {
            return null;
        }
        Object data = body.get("data");
        if (data instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return body;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Boolean asBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value == null) {
            return null;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    @Data
    @Builder
    public static class ProvisionResult {
        private String userId;
        private String temporaryPassword;
        private Boolean passwordResetRequired;
        private boolean created;

        public static ProvisionResult empty() {
            return ProvisionResult.builder().build();
        }
    }
}
