package com.plp.lending.integration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProgramServiceEffectiveBorrowerTermsClient {

    private final RestTemplate restTemplate;

    public record EffectiveBorrowerTerms(
            BigDecimal interestRate,
            BigDecimal discountMarginPercent,
            Integer creditPeriodDays,
            String discountHold,
            String paymentMethod,
            BigDecimal overdueInterestRate) {}

    @SuppressWarnings("unchecked")
    public Optional<EffectiveBorrowerTerms> fetchEffectiveTerms(UUID subProgramId, UUID borrowerId) {
        if (subProgramId == null || borrowerId == null) {
            return Optional.empty();
        }
        try {
            Map<String, Object> resp = restTemplate.exchange(
                            "http://program-service/api/v1/sub-programs/{subProgramId}/borrowers/{borrowerId}/effective-terms",
                            HttpMethod.GET,
                            new HttpEntity<>(ProgramServiceAuthHeaders.trustedInternalHeaders()),
                            Map.class,
                            subProgramId,
                            borrowerId)
                    .getBody();
            if (resp == null || !"SUCCESS".equals(resp.get("status"))) {
                return Optional.empty();
            }
            Object dataObj = resp.get("data");
            if (!(dataObj instanceof Map<?, ?> row)) {
                return Optional.empty();
            }
            return Optional.of(new EffectiveBorrowerTerms(
                    parseBigDecimal(row.get("interestRate")),
                    parseBigDecimal(row.get("discountMarginPercent")),
                    parseInteger(row.get("creditPeriodDays")),
                    row.get("discountHold") != null ? row.get("discountHold").toString() : "NO",
                    row.get("paymentMethod") != null ? row.get("paymentMethod").toString() : "SMART_COLLECT",
                    parseBigDecimal(row.get("overdueInterestRate"))));
        } catch (Exception e) {
            log.warn(
                    "Failed to fetch effective borrower terms subProgram={} borrower={}: {}",
                    subProgramId,
                    borrowerId,
                    e.getMessage());
            return Optional.empty();
        }
    }

    private static BigDecimal parseBigDecimal(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof BigDecimal bd) {
            return bd;
        }
        if (raw instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(raw.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseInteger(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(raw.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
