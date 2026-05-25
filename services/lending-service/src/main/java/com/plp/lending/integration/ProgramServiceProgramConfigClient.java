package com.plp.lending.integration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * Loads {@code Program.config} from program-service for invoice-discounting rule evaluation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProgramServiceProgramConfigClient {

    private final RestTemplate restTemplate;

    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchProgramConfig(UUID programId) {
        if (programId == null) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> resp = restTemplate.exchange(
                            "http://program-service/api/v1/programs/{programId}",
                            HttpMethod.GET,
                            new HttpEntity<>(ProgramServiceAuthHeaders.trustedInternalHeaders()),
                            Map.class,
                            programId)
                    .getBody();
            if (resp != null && "SUCCESS".equals(resp.get("status"))) {
                Object dataObj = resp.get("data");
                if (dataObj instanceof Map<?, ?> dataMap) {
                    Object cfg = dataMap.get("config");
                    if (cfg instanceof Map<?, ?> m) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> typed = (Map<String, Object>) m;
                        return typed;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch program {} config: {}", programId, e.getMessage());
        }
        return Collections.emptyMap();
    }
}
