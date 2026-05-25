package com.plp.lending.lms;

import com.plp.lending.integration.ProgramServiceAuthHeaders;
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
public class ProgramLmsConfigClient {

    private final RestTemplate restTemplate;

    public ProgramLmsConfig fetch(UUID programId) {
        try {
            HttpEntity<Void> entity = new HttpEntity<>(ProgramServiceAuthHeaders.trustedInternalHeaders());
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.exchange(
                    "http://program-service/api/v1/programs/{programId}",
                    HttpMethod.GET,
                    entity,
                    Map.class,
                    programId).getBody();
            Map<String, Object> program = unwrapSuccessData(response);
            if (program.isEmpty()) {
                log.warn("Program LMS config empty for programId={}", programId);
                return ProgramLmsConfig.builder().lmsEnabled(false).build();
            }
            String lmsEntry = stringVal(program.get("lmsEntryIn"));
            boolean enabled = "YES".equalsIgnoreCase(lmsEntry);
            log.debug("Program LMS config programId={} lmsEntryIn={} encoreProductCode={}",
                    programId, lmsEntry, program.get("encoreProductCode"));
            return ProgramLmsConfig.builder()
                    .lmsEnabled(enabled)
                    .encoreProductCode(stringVal(program.get("encoreProductCode")))
                    .build();
        } catch (Exception e) {
            log.warn("Failed to load program LMS config for {}: {}", programId, e.getMessage());
            return ProgramLmsConfig.builder().lmsEnabled(false).build();
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchBorrower(UUID borrowerId) {
        try {
            HttpEntity<Void> entity = new HttpEntity<>(ProgramServiceAuthHeaders.trustedInternalHeaders());
            Map<String, Object> response = restTemplate.exchange(
                    "http://program-service/api/v1/borrowers/{borrowerId}",
                    HttpMethod.GET,
                    entity,
                    Map.class,
                    borrowerId).getBody();
            return unwrapSuccessData(response);
        } catch (Exception e) {
            log.warn("Failed to fetch borrower {}: {}", borrowerId, e.getMessage());
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> unwrapSuccessData(Map<String, Object> response) {
        if (response == null) {
            return Map.of();
        }
        if ("SUCCESS".equals(response.get("status")) && response.get("data") instanceof Map<?, ?> data) {
            return (Map<String, Object>) data;
        }
        return response;
    }

    private static String stringVal(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }
}
