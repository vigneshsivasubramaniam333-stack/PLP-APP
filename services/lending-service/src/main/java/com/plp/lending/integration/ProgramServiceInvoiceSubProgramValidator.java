package com.plp.lending.integration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Validates invoice ↔ loan request alignment when an invoice is linked to a sub-program
 * (reads invoice JSON from program-service and sub-program borrowers via
 * {@code GET /api/v1/sub-programs/{id}/borrowers}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProgramServiceInvoiceSubProgramValidator {

    static final String NOT_ENROLLED_MESSAGE = "Borrower is not enrolled in the invoice sub-program";

    private final RestTemplate restTemplate;

    /**
     * @return empty if no {@code subProgramId} on the invoice or all checks pass; otherwise the rejection reason.
     */
    public Optional<String> validateInvoiceSubProgramLink(
            Map<String, Object> invoiceJson,
            UUID requestBorrowerId,
            UUID requestProgramId) {

        UUID subProgramId = extractUuid(invoiceJson.get("subProgramId"));
        if (subProgramId == null) {
            return Optional.empty();
        }

        UUID invoiceBorrowerId = extractUuid(invoiceJson.get("borrowerId"));
        UUID invoiceProgramId = extractUuid(invoiceJson.get("programId"));

        if (invoiceBorrowerId == null || !invoiceBorrowerId.equals(requestBorrowerId)) {
            return Optional.of(NOT_ENROLLED_MESSAGE);
        }
        if (invoiceProgramId == null || !invoiceProgramId.equals(requestProgramId)) {
            return Optional.of(NOT_ENROLLED_MESSAGE);
        }
        if (!borrowerListedUnderSubProgram(subProgramId, requestBorrowerId)) {
            return Optional.of(NOT_ENROLLED_MESSAGE);
        }
        return Optional.empty();
    }

    private boolean borrowerListedUnderSubProgram(UUID subProgramId, UUID borrowerId) {
        try {
            HttpEntity<Void> entity = new HttpEntity<>(ProgramServiceAuthHeaders.trustedInternalHeaders());
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.exchange(
                            "http://program-service/api/v1/sub-programs/{subProgramId}/borrowers",
                            HttpMethod.GET,
                            entity,
                            Map.class,
                            subProgramId)
                    .getBody();
            if (resp == null || !"SUCCESS".equals(resp.get("status"))) {
                return false;
            }
            Object data = resp.get("data");
            if (!(data instanceof List<?> list)) {
                return false;
            }
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> row)) {
                    continue;
                }
                UUID bid = extractUuid(row.get("borrowerId"));
                if (borrowerId.equals(bid)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.warn("Failed to fetch borrowers for sub-program {}: {}", subProgramId, e.getMessage());
            return false;
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
