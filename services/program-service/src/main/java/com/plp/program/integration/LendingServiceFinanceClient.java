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

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Trusted program-service → lending-service calls for auto-pull finance requests.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LendingServiceFinanceClient {

    private static final String INTERNAL_ROLES = "PLATFORM_ADMIN";
    private static final String INTERNAL_USER_ID = "SYSTEM";

    private final RestTemplateBuilder restTemplateBuilder;

    @Value("${plp.lending.base-url:http://localhost:8183}")
    private String lendingBaseUrl;

    public boolean requestInvoiceFinance(UUID invoiceId, UUID borrowerId, UUID programId, BigDecimal requestedAmount) {
        if (requestedAmount == null || requestedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Auto-pull skipped for invoice {}: no financeable amount", invoiceId);
            return false;
        }
        try {
            RestTemplate restTemplate = restTemplateBuilder.build();
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-User-Roles", INTERNAL_ROLES);
            headers.set("X-User-Id", INTERNAL_USER_ID);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("productType", "INVOICE_DISCOUNTING");
            body.put("borrowerId", borrowerId.toString());
            body.put("programId", programId.toString());
            body.put("invoiceId", invoiceId.toString());
            body.put("requestedAmount", requestedAmount);

            restTemplate.exchange(
                    lendingBaseUrl.replaceAll("/$", "") + "/api/v1/loans",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class);
            log.info("Auto-pull finance requested for invoice {} amount {}", invoiceId, requestedAmount);
            return true;
        } catch (Exception e) {
            log.error("Auto-pull finance request failed for invoice {}: {}", invoiceId, e.getMessage());
            return false;
        }
    }
}
