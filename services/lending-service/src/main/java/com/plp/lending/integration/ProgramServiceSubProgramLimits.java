package com.plp.lending.integration;

import com.plp.lending.exception.RestClientIntegrationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Sub-program exposure limits on program-service (invoice discounting).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProgramServiceSubProgramLimits {

    public static final String REQUEST_EXCEEDS_SUB_PROGRAM_LIMIT = "Requested amount exceeds sub-program available limit";

    /**
     * Sub-program aggregate and membership headroom using the same formula as program-service
     * {@code SubProgramLimitService#effectiveAvailable} applied to each tier.
     */
    public record DualSubProgramBorrowerAvailability(
            BigDecimal subProgramAvailable, BigDecimal borrowerAvailable, BigDecimal effectiveAvailable) {}

    private final RestTemplate restTemplate;

    /**
     * When invoice JSON includes {@code subProgramId}, ensures requested amount fits sub-program and membership headroom.
     */
    public Optional<String> validateInvoiceSubProgramLimits(
            Map<String, Object> invoiceJson,
            UUID borrowerId,
            BigDecimal requestedAmount) {

        UUID subProgramId = extractUuid(invoiceJson != null ? invoiceJson.get("subProgramId") : null);
        if (subProgramId == null || requestedAmount == null) {
            return Optional.empty();
        }
        return validateRequestedAmountWithinSubProgram(subProgramId, borrowerId, requestedAmount);
    }

    /**
     * Membership headroom (same formula as program-service {@code SubProgramLimitService#effectiveAvailable}).
     * Empty when the borrower is not enrolled, the sub-program is missing, or the call fails.
     */
    public Optional<BigDecimal> fetchEffectiveBorrowerAvailableLimit(UUID subProgramId, UUID borrowerId) {
        try {
            HttpEntity<Void> entity = new HttpEntity<>(ProgramServiceAuthHeaders.trustedInternalHeaders());
            @SuppressWarnings("unchecked")
            Map<String, Object> memResp = restTemplate.exchange(
                            "http://program-service/api/v1/sub-programs/{id}/borrowers/{borrowerId}/limit-summary",
                            HttpMethod.GET,
                            entity,
                            Map.class,
                            subProgramId,
                            borrowerId)
                    .getBody();
            Map<String, Object> memData = unwrapData(memResp);
            if (memData == null) {
                return Optional.empty();
            }
            return Optional.of(effectiveBorrowerHeadroom(memData));
        } catch (Exception e) {
            log.debug("Borrower limit-summary unavailable subProgram={} borrower={}: {}",
                    subProgramId, borrowerId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * GET sub-program aggregate + borrower membership limit-summary, returning min of both effective headrooms (same formula as disburse).
     */
    public Optional<DualSubProgramBorrowerAvailability> fetchDualLimitHeadroom(UUID subProgramId, UUID borrowerId) {
        try {
            HttpEntity<Void> entity = new HttpEntity<>(ProgramServiceAuthHeaders.trustedInternalHeaders());
            @SuppressWarnings("unchecked")
            Map<String, Object> subResp =
                    restTemplate.exchange(
                                    "http://program-service/api/v1/sub-programs/{id}/limit-summary",
                                    HttpMethod.GET,
                                    entity,
                                    Map.class,
                                    subProgramId)
                            .getBody();
            @SuppressWarnings("unchecked")
            Map<String, Object> memResp =
                    restTemplate.exchange(
                                    "http://program-service/api/v1/sub-programs/{id}/borrowers/{borrowerId}/limit-summary",
                                    HttpMethod.GET,
                                    entity,
                                    Map.class,
                                    subProgramId,
                                    borrowerId)
                            .getBody();

            Map<String, Object> subData = unwrapData(subResp);
            Map<String, Object> memData = unwrapData(memResp);
            if (subData == null || memData == null) {
                return Optional.empty();
            }
            BigDecimal subProgramAvailable = effectiveSubProgramHeadroom(subData);
            BigDecimal borrowerAvailable = effectiveBorrowerHeadroom(memData);
            BigDecimal effectiveAvailable = subProgramAvailable.min(borrowerAvailable);
            return Optional.of(new DualSubProgramBorrowerAvailability(subProgramAvailable, borrowerAvailable, effectiveAvailable));
        } catch (Exception e) {
            log.debug("Dual limit-summary unavailable subProgram={} borrower={}: {}", subProgramId, borrowerId, e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<String> validateRequestedAmountWithinSubProgram(
            UUID subProgramId,
            UUID borrowerId,
            BigDecimal requestedAmount) {

        try {
            HttpEntity<Void> entity = new HttpEntity<>(ProgramServiceAuthHeaders.trustedInternalHeaders());
            @SuppressWarnings("unchecked")
            Map<String, Object> subResp = restTemplate.exchange(
                            "http://program-service/api/v1/sub-programs/{id}/limit-summary",
                            HttpMethod.GET,
                            entity,
                            Map.class,
                            subProgramId)
                    .getBody();
            @SuppressWarnings("unchecked")
            Map<String, Object> memResp = restTemplate.exchange(
                            "http://program-service/api/v1/sub-programs/{id}/borrowers/{borrowerId}/limit-summary",
                            HttpMethod.GET,
                            entity,
                            Map.class,
                            subProgramId,
                            borrowerId)
                    .getBody();

            Map<String, Object> subData = unwrapData(subResp);
            Map<String, Object> memData = unwrapData(memResp);
            if (subData == null || memData == null) {
                return Optional.of(REQUEST_EXCEEDS_SUB_PROGRAM_LIMIT);
            }

            BigDecimal subAvail = effectiveSubProgramHeadroom(subData);
            BigDecimal memAvail = effectiveBorrowerHeadroom(memData);
            if (requestedAmount.compareTo(subAvail) > 0 || requestedAmount.compareTo(memAvail) > 0) {
                return Optional.of(REQUEST_EXCEEDS_SUB_PROGRAM_LIMIT);
            }
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Sub-program limit summary failed subProgram={} borrower={}: {}",
                    subProgramId, borrowerId, e.getMessage());
            return Optional.of(REQUEST_EXCEEDS_SUB_PROGRAM_LIMIT);
        }
    }

    public UUID fetchSubProgramIdFromInvoice(UUID invoiceId) {
        if (invoiceId == null) {
            return null;
        }
        try {
            HttpEntity<Void> entity = new HttpEntity<>(ProgramServiceAuthHeaders.trustedInternalHeaders());
            @SuppressWarnings("unchecked")
            Map<String, Object> inv = restTemplate.exchange(
                            "http://program-service/api/v1/invoices/{invoiceId}",
                            HttpMethod.GET,
                            entity,
                            Map.class,
                            invoiceId)
                    .getBody();
            return extractUuid(inv != null ? inv.get("subProgramId") : null);
        } catch (Exception e) {
            log.warn("Could not load invoice {} for sub-program link: {}", invoiceId, e.getMessage());
            return null;
        }
    }

    public void blockSubProgramLimits(UUID subProgramId, UUID borrowerId, BigDecimal amount) {
        Map<String, Object> body = new HashMap<>();
        body.put("borrowerId", borrowerId.toString());
        body.put("amount", amount);
        HttpEntity<Map<String, Object>> entity =
                new HttpEntity<>(body, ProgramServiceAuthHeaders.trustedInternalJsonHeaders());
        try {
            restTemplate.exchange(
                    "http://program-service/api/v1/sub-programs/{id}/limits/block",
                    HttpMethod.POST,
                    entity,
                    Map.class,
                    subProgramId);
        } catch (RestClientResponseException e) {
            throw RestClientIntegrationMapper.toBusinessException(e, "Unable to block sub-program limit");
        }
    }

    public void releaseSubProgramLimits(UUID subProgramId, UUID borrowerId, BigDecimal amount) {
        Map<String, Object> body = new HashMap<>();
        body.put("borrowerId", borrowerId.toString());
        body.put("amount", amount);
        HttpEntity<Map<String, Object>> entity =
                new HttpEntity<>(body, ProgramServiceAuthHeaders.trustedInternalJsonHeaders());
        try {
            restTemplate.exchange(
                    "http://program-service/api/v1/sub-programs/{id}/limits/release",
                    HttpMethod.POST,
                    entity,
                    Map.class,
                    subProgramId);
        } catch (RestClientResponseException e) {
            throw RestClientIntegrationMapper.toBusinessException(e, "Unable to release sub-program limit");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> unwrapData(Map<String, Object> response) {
        if (response == null || !"SUCCESS".equals(response.get("status"))) {
            return null;
        }
        Object data = response.get("data");
        return data instanceof Map ? (Map<String, Object>) data : null;
    }

    /**
     * Effective headroom: same formula as program-service {@code SubProgramLimitService#effectiveAvailable}.
     *
     * @param cap {@code subProgramLimit} or {@code borrowerLimit} from limit-summary payloads.
     */
    private static BigDecimal effectiveHeadroom(BigDecimal availableLimit, BigDecimal cap, BigDecimal utilized) {
        BigDecimal u = utilized != null ? utilized : BigDecimal.ZERO;
        if (availableLimit != null && cap != null && utilized != null) {
            BigDecimal computed = cap.subtract(utilized);
            return availableLimit.min(computed).max(BigDecimal.ZERO);
        }
        if (cap != null) {
            return cap.subtract(u).max(BigDecimal.ZERO);
        }
        return BigDecimal.ZERO;
    }

    /** Aggregate sub-program row from GET .../limit-summary (keys: {@code availableLimit}, {@code subProgramLimit}, {@code utilizedLimit}). */
    private static BigDecimal effectiveSubProgramHeadroom(Map<String, Object> subData) {
        BigDecimal availableLimit = toAmountNullable(subData.get("availableLimit"));
        BigDecimal cap = toAmountNullable(subData.get("subProgramLimit"));
        BigDecimal utilized = toAmountNullable(subData.get("utilizedLimit"));
        return effectiveHeadroom(availableLimit, cap, utilized);
    }

    /** Membership row from GET .../borrowers/.../limit-summary (keys: {@code availableLimit}, {@code borrowerLimit}, {@code utilizedLimit}). */
    private static BigDecimal effectiveBorrowerHeadroom(Map<String, Object> memData) {
        BigDecimal availableLimit = toAmountNullable(memData.get("availableLimit"));
        BigDecimal cap = toAmountNullable(memData.get("borrowerLimit"));
        BigDecimal utilized = toAmountNullable(memData.get("utilizedLimit"));
        return effectiveHeadroom(availableLimit, cap, utilized);
    }

    private static BigDecimal toAmountNullable(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof BigDecimal b) {
            return b;
        }
        if (raw instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(raw.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static UUID extractUuid(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof UUID u) {
            return u;
        }
        String s = raw.toString().trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
