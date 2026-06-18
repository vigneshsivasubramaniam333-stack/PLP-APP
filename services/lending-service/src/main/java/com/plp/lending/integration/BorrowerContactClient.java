package com.plp.lending.integration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class BorrowerContactClient {

    private final RestTemplate restTemplate;

    public record BorrowerContact(String name, String email) {}

    public BorrowerContact fetch(UUID borrowerId) {
        if (borrowerId == null) {
            return new BorrowerContact("Borrower", null);
        }
        try {
            HttpEntity<Void> entity = new HttpEntity<>(ProgramServiceAuthHeaders.trustedInternalHeaders());
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.exchange(
                            "http://program-service/api/v1/borrowers/{borrowerId}",
                            HttpMethod.GET,
                            entity,
                            Map.class,
                            borrowerId)
                    .getBody();
            if (response == null || !"SUCCESS".equals(response.get("status"))) {
                return new BorrowerContact("Borrower", null);
            }
            Object dataObj = response.get("data");
            if (!(dataObj instanceof Map<?, ?> dataMap)) {
                return new BorrowerContact("Borrower", null);
            }
            String name = dataMap.get("name") != null ? String.valueOf(dataMap.get("name")) : "Borrower";
            String email = dataMap.get("email") != null ? String.valueOf(dataMap.get("email")).trim() : null;
            if (email != null && email.isBlank()) {
                email = null;
            }
            return new BorrowerContact(name, email);
        } catch (Exception e) {
            log.warn("Failed to fetch borrower contact {}: {}", borrowerId, e.getMessage());
            return new BorrowerContact("Borrower", null);
        }
    }
}
