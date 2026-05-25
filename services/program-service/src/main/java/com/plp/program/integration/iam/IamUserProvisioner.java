package com.plp.program.integration.iam;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
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

    public void provisionAnchorAdmin(UUID plpAnchorId, String email, String fullName, String phone) {
        provisionPortalUser(plpAnchorId, "ANCHOR", "ANCHOR_ADMIN", email, fullName, phone);
    }

    public void provisionBorrowerUser(UUID plpBorrowerId, String email, String fullName, String phone) {
        provisionPortalUser(plpBorrowerId, "BORROWER", "BORROWER", email, fullName, phone);
    }

    private void provisionPortalUser(
            UUID linkedEntityId,
            String linkedEntityType,
            String role,
            String email,
            String fullName,
            String phone) {
        if (email == null || email.isBlank()) {
            log.info(
                    "Skipping PLP portal user provision — no email for {} {}",
                    linkedEntityType,
                    linkedEntityId);
            return;
        }
        if (integrationApiKey == null || integrationApiKey.isBlank()) {
            log.warn(
                    "Skipping PLP portal user provision — plp.los-integration.api-key is not configured ({}/{})",
                    linkedEntityType,
                    linkedEntityId);
            return;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email.trim());
        body.put("fullName", fullName != null && !fullName.isBlank() ? fullName.trim() : email.trim());
        body.put("phone", phone);
        body.put("role", role);
        body.put("linkedEntityId", linkedEntityId);
        body.put("linkedEntityType", linkedEntityType);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(INTEGRATION_API_KEY_HEADER, integrationApiKey);

        try {
            RestTemplate restTemplate = restTemplateBuilder.build();
            ResponseEntity<Map> response =
                    restTemplate.exchange(
                            iamBaseUrl + "/api/v1/integrations/los/users",
                            HttpMethod.POST,
                            new HttpEntity<>(body, headers),
                            Map.class);
            log.info(
                    "PLP portal user provision response for {} {}: status={}",
                    linkedEntityType,
                    linkedEntityId,
                    response.getStatusCode());
        } catch (RestClientException e) {
            log.error(
                    "PLP portal user provision failed for {} {} email={}: {}",
                    linkedEntityType,
                    linkedEntityId,
                    email,
                    e.getMessage());
        }
    }
}
