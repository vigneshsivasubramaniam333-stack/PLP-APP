package com.plp.lending.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientResponseException;

import java.util.Locale;

/**
 * Maps program-service (and other internal) HTTP failures into {@link LendingBusinessException}
 * with stable user-facing messages.
 */
public final class RestClientIntegrationMapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String MSG_NO_ACTIVE_PAY_LOAN_SUB = "No active Pay Loan sub-program found for borrower";
    private static final String MSG_INSUFFICIENT = "Available limit is insufficient";
    private static final String MSG_NOT_LINKED_SUB = "Borrower is not linked to the selected sub-program";
    private static final String MSG_DUPLICATE_FINANCING = "A financing request already exists for this invoice";

    private RestClientIntegrationMapper() {}

    public static LendingBusinessException toBusinessException(RestClientResponseException e, String fallbackMessage) {
        String raw = firstNonBlank(extractJsonMessage(e.getResponseBodyAsString()), e.getStatusText());
        String polished = polishMessage(raw);
        String message = firstNonBlank(polished, raw, fallbackMessage);
        int code = e.getStatusCode().value();
        HttpStatus status = mapStatus(code, message);
        return new LendingBusinessException(status, message);
    }

    private static HttpStatus mapStatus(int downstreamCode, String message) {
        if (downstreamCode == 409) {
            return HttpStatus.CONFLICT;
        }
        if (downstreamCode >= 400 && downstreamCode < 500) {
            HttpStatus resolved = HttpStatus.resolve(downstreamCode);
            return resolved != null ? resolved : HttpStatus.BAD_REQUEST;
        }
        if (downstreamCode >= 500 && looksLikeBusinessValidation(message)) {
            return HttpStatus.BAD_REQUEST;
        }
        return HttpStatus.BAD_GATEWAY;
    }

    private static boolean looksLikeBusinessValidation(String msg) {
        if (msg == null) {
            return false;
        }
        String m = msg.toLowerCase(Locale.ROOT);
        return m.contains("limit")
                || m.contains("enrolled")
                || m.contains("exceed")
                || m.contains("not eligible")
                || m.contains("financing")
                || m.contains("borrower")
                || m.contains("invoice")
                || m.contains("sub program")
                || m.contains("salary")
                || m.contains("ambiguous");
    }

    private static String polishMessage(String raw) {
        if (raw == null) {
            return null;
        }
        if (raw.contains("Borrower not enrolled in sub program")) {
            return MSG_NOT_LINKED_SUB;
        }
        if (raw.contains("Requested amount exceeds sub-program available limit")) {
            return MSG_INSUFFICIENT;
        }
        if (raw.contains("Limit not found for borrower")) {
            return MSG_INSUFFICIENT;
        }
        return null;
    }

    private static String extractJsonMessage(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode n = OBJECT_MAPPER.readTree(body);
            if (n.has("message") && n.get("message").isTextual()) {
                return n.get("message").asText();
            }
            if (n.has("error") && n.get("error").isTextual()) {
                return n.get("error").asText();
            }
            if (n.has("data") && n.get("data").isTextual()) {
                return n.get("data").asText();
            }
        } catch (Exception ignored) {
            return body.trim();
        }
        return null;
    }

    private static String firstNonBlank(String... parts) {
        if (parts == null) {
            return "";
        }
        for (String p : parts) {
            if (p != null) {
                String t = p.trim();
                if (!t.isEmpty()) {
                    return t;
                }
            }
        }
        return "";
    }

    /** User-facing wording aligned with eligibility and request-layer checks. */
    public static LendingBusinessException noActivePayLoanSubProgram() {
        return new LendingBusinessException(HttpStatus.BAD_REQUEST, MSG_NO_ACTIVE_PAY_LOAN_SUB);
    }

    public static LendingBusinessException noSubProgramForInvoiceDiscounting() {
        return new LendingBusinessException(
                HttpStatus.BAD_REQUEST, "No sub-program found for invoice discounting");
    }

    public static String duplicateInvoiceFinancingMessage() {
        return MSG_DUPLICATE_FINANCING;
    }
}
