package com.plp.integration.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Public PayU browser callbacks (no JWT). Delegates to lending-service for hash verification and PIP creation.
 */
@RestController
@RequestMapping("/api/v1/webhooks/payments/payu")
@RequiredArgsConstructor
@Slf4j
public class PayuPaymentWebhookController {

    private final RestTemplate restTemplate;

    @PostMapping(value = "/success", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> success(@RequestParam Map<String, String> params) {
        log.info("PayU success callback txn={} keys={}", params.get("txnid"), params.keySet());
        return htmlRedirect(delegate("/api/v1/internal/payments/payu/success", params));
    }

    @PostMapping(value = "/failure", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> failure(@RequestParam Map<String, String> params) {
        return htmlRedirect(delegate("/api/v1/internal/payments/payu/failure", params));
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<String> htmlRedirect(String redirectUrl) {
        String safe = redirectUrl.replace("\"", "%22");
        String html = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\">"
                + "<meta http-equiv=\"refresh\" content=\"0;url=" + safe + "\">"
                + "<title>Redirecting…</title></head><body><p>Redirecting to payment result…</p>"
                + "<script>location.href=" + jsonString(redirectUrl) + ";</script></body></html>";
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
    }

    @SuppressWarnings("unchecked")
    private String delegate(String path, Map<String, String> params) {
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            params.forEach(form::add);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(form, headers);
            Map<String, String> body = restTemplate
                    .exchange("http://lending-service" + path, HttpMethod.POST, entity, Map.class)
                    .getBody();
            if (body == null || body.get("redirectUrl") == null) {
                log.warn("PayU delegate returned empty redirect for path {}", path);
                return fallbackRedirect(params, path.contains("success"));
            }
            return body.get("redirectUrl");
        } catch (Exception e) {
            log.error("PayU delegate to lending-service failed for {}: {}", path, e.getMessage(), e);
            return fallbackRedirect(params, path.contains("success"));
        }
    }

    private String fallbackRedirect(Map<String, String> params, boolean successPath) {
        String txnid = params.getOrDefault("txnid", "");
        String status = successPath && "success".equalsIgnoreCase(String.valueOf(params.get("status")))
                ? "success"
                : "failure";
        return "/payments/result?status=" + status + "&txnId=" + txnid;
    }

    private static String jsonString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
