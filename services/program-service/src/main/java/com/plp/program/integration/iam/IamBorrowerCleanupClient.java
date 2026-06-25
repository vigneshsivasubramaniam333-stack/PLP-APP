package com.plp.program.integration.iam;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class IamBorrowerCleanupClient {

    private final RestTemplateBuilder restTemplateBuilder;

    @Value("${plp.iam.base-url:http://localhost:8081}")
    private String iamBaseUrl;

    @Value("${plp.los-integration.api-key:}")
    private String integrationApiKey;

    public boolean deleteBorrowerPortalUser(UUID plpBorrowerId) {
        if (plpBorrowerId == null || integrationApiKey == null || integrationApiKey.isBlank()) {
            return false;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(IamUserProvisioner.INTEGRATION_API_KEY_HEADER, integrationApiKey);
            RestTemplate restTemplate = restTemplateBuilder.build();
            restTemplate.exchange(
                    iamBaseUrl + "/api/v1/integrations/los/users/by-linked-entity?linkedEntityType=BORROWER&linkedEntityId="
                            + plpBorrowerId,
                    HttpMethod.DELETE,
                    new HttpEntity<>(headers),
                    Void.class);
            return true;
        } catch (Exception e) {
            log.warn("IAM borrower user cleanup failed for {}: {}", plpBorrowerId, e.getMessage());
            return false;
        }
    }
}
