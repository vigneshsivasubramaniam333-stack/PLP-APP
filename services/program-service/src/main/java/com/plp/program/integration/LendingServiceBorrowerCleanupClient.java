package com.plp.program.integration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class LendingServiceBorrowerCleanupClient {

    private final RestTemplateBuilder restTemplateBuilder;

    @Value("${plp.lending.base-url:http://localhost:8183}")
    private String lendingBaseUrl;

    public int cleanupBorrowerLoans(UUID plpBorrowerId) {
        if (plpBorrowerId == null) {
            return 0;
        }
        try {
            RestTemplate restTemplate = restTemplateBuilder.build();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(Map.of("sourceSystem", "LOS"), headers);
            String url = lendingBaseUrl.replaceAll("/$", "")
                    + "/api/v1/integrations/los/borrowers/{borrowerId}/cleanup";
            @SuppressWarnings("unchecked")
            Map<String, Object> body = restTemplate.exchange(
                            url,
                            HttpMethod.POST,
                            entity,
                            Map.class,
                            plpBorrowerId)
                    .getBody();
            if (body == null) {
                return 0;
            }
            Object data = body.get("data");
            if (data instanceof Map<?, ?> map && map.get("loansRemoved") != null) {
                return Integer.parseInt(String.valueOf(map.get("loansRemoved")));
            }
            return 0;
        } catch (Exception e) {
            log.error("Lending cleanup failed for borrower {}: {}", plpBorrowerId, e.getMessage());
            throw new RuntimeException("Lending cleanup failed: " + e.getMessage(), e);
        }
    }
}
