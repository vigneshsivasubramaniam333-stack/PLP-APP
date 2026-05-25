package com.plp.lending.integration;

import com.plp.lending.exception.LendingBusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Trusted reads/updates for salary slips in program-service (lender internal headers).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProgramServiceSalarySlipClient {

    private final RestTemplate restTemplate;

    @SuppressWarnings("unchecked")
    public Optional<Map<String, Object>> getSalarySlip(UUID salaryDataId) {
        try {
            HttpEntity<Void> entity = new HttpEntity<>(ProgramServiceAuthHeaders.trustedInternalHeaders());
            Map<String, Object> resp =
                    restTemplate.exchange(
                                    "http://program-service/api/v1/salary/{salaryId}",
                                    HttpMethod.GET,
                                    entity,
                                    Map.class,
                                    salaryDataId)
                            .getBody();
            if (resp == null || !"SUCCESS".equals(resp.get("status"))) {
                return Optional.empty();
            }
            Object data = resp.get("data");
            if (!(data instanceof Map<?, ?> m)) {
                return Optional.empty();
            }
            return Optional.of((Map<String, Object>) m);
        } catch (Exception e) {
            log.warn("Failed to GET salary slip {}: {}", salaryDataId, e.getMessage());
            return Optional.empty();
        }
    }

    public void patchSlipStatus(UUID salaryDataId, String slipStatusEnumName) {
        try {
            restTemplate.exchange(
                    "http://program-service/api/v1/salary/{salaryId}/slip-status",
                    HttpMethod.POST,
                    new HttpEntity<>(
                            Map.of("status", slipStatusEnumName),
                            ProgramServiceAuthHeaders.trustedInternalJsonHeaders()),
                    Map.class,
                    salaryDataId);
            log.debug("Salary slip {} status -> {}", salaryDataId, slipStatusEnumName);
        } catch (Exception e) {
            log.warn("Failed POST salary slip status {} -> {}: {}", salaryDataId, slipStatusEnumName, e.getMessage());
            throw new LendingBusinessException(
                    HttpStatus.BAD_GATEWAY, "Failed to update salary slip status: " + e.getMessage());
        }
    }
}
