package com.plp.lending.integration;

import com.plp.lending.validation.ProgramParametersReader;
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
public class ProgramServiceProgramParametersClient {

    private final RestTemplate restTemplate;

    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchProgramParameters(UUID programId) {
        if (programId == null) {
            return ProgramParametersReader.defaultParameters();
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
                    Object params = dataMap.get("parameters");
                    if (params instanceof Map<?, ?> m) {
                        return ProgramParametersReader.normalize((Map<String, Object>) m);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch program {} parameters: {}", programId, e.getMessage());
        }
        return ProgramParametersReader.defaultParameters();
    }
}
