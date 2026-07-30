package com.plp.program.integration.los;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Outbound client the PLP anchor onboarding portal uses to load/save the delegated anchor's
 * LOS loan application and to submit/resubmit intake, calling the inbound integration surface
 * exposed by los-core-service at {@code /api/v1/integrations/plp/anchor-applications/**}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LosAnchorApplicationClient {

    public static final String INTEGRATION_KEY_HEADER = "X-Plp-Integration-Key";

    private final RestTemplateBuilder restTemplateBuilder;

    @Value("${plp.los.base-url:http://localhost:8083}")
    private String losBaseUrl;

    /** Shared secret for LOS inbound PLP anchor APIs. Do not reuse plp.los-integration.api-key (IAM). */
    @Value("${plp.los.anchor-integration-api-key:${plp.los.integration-api-key:}}")
    private String integrationApiKey;

    @Value("${plp.los.enabled:true}")
    private boolean enabled;

    public boolean isConfigured() {
        return enabled && integrationApiKey != null && !integrationApiKey.isBlank()
                && losBaseUrl != null && !losBaseUrl.isBlank();
    }

    public Map<String, Object> getApplication(String applicationId) {
        return exchange(HttpMethod.GET, "/api/v1/integrations/plp/anchor-applications/" + applicationId, null);
    }

    public Map<String, Object> getIntakeContext(String applicationId) {
        return exchange(HttpMethod.GET,
                "/api/v1/integrations/plp/anchor-applications/" + applicationId + "/intake-context", null);
    }

    public Map<String, Object> updateApplication(String applicationId, Map<String, Object> payload) {
        return exchange(HttpMethod.PUT, "/api/v1/integrations/plp/anchor-applications/" + applicationId, payload);
    }

    public Map<String, Object> submit(String applicationId) {
        return exchange(HttpMethod.POST, "/api/v1/integrations/plp/anchor-applications/" + applicationId + "/submit", null);
    }

    public Map<String, Object> resubmit(String applicationId) {
        return exchange(HttpMethod.POST, "/api/v1/integrations/plp/anchor-applications/" + applicationId + "/resubmit", null);
    }

    public List<Map<String, Object>> listStates() {
        return exchangeList(HttpMethod.GET, "/api/v1/integrations/plp/geo/states");
    }

    public List<Map<String, Object>> listCities(UUID stateId) {
        return exchangeList(HttpMethod.GET, "/api/v1/integrations/plp/geo/states/" + stateId + "/cities");
    }

    public List<Map<String, Object>> listDocuments(String applicationId) {
        return exchangeList(HttpMethod.GET,
                "/api/v1/integrations/plp/anchor-applications/" + applicationId + "/documents");
    }

    public ResponseEntity<byte[]> downloadDocument(String applicationId, String documentId, boolean inline) {
        if (!isConfigured()) {
            throw new LosIntegrationException(
                    "LOS anchor integration is not configured (plp.los.base-url / plp.los.integration-api-key)");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set(INTEGRATION_KEY_HEADER, integrationApiKey);
        String path = "/api/v1/integrations/plp/anchor-applications/" + applicationId
                + "/documents/" + documentId + (inline ? "/content" : "/download");
        try {
            RestTemplate restTemplate = restTemplateBuilder.build();
            return restTemplate.exchange(
                    trimSlash(losBaseUrl) + path,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    byte[].class);
        } catch (HttpStatusCodeException e) {
            throw wrapHttp(e, "GET " + path);
        } catch (RestClientException e) {
            log.error("LOS document stream failed [{}]: {}", path, e.getMessage());
            throw new LosIntegrationException("LOS request failed: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> uploadDocument(String applicationId, String documentType, MultipartFile file) {
        if (!isConfigured()) {
            throw new LosIntegrationException(
                    "LOS anchor integration is not configured (plp.los.base-url / plp.los.integration-api-key)");
        }
        try {
            byte[] bytes = file.getBytes();
            String filename = file.getOriginalFilename() != null && !file.getOriginalFilename().isBlank()
                    ? file.getOriginalFilename()
                    : "upload.bin";
            ByteArrayResource resource = new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", resource);
            body.add("documentType", documentType);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set(INTEGRATION_KEY_HEADER, integrationApiKey);

            RestTemplate restTemplate = restTemplateBuilder.build();
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    trimSlash(losBaseUrl) + "/api/v1/integrations/plp/anchor-applications/" + applicationId + "/documents",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    new ParameterizedTypeReference<>() {});
            return response.getBody() != null ? response.getBody() : Map.of();
        } catch (IOException e) {
            throw new LosIntegrationException("Failed to read upload bytes: " + e.getMessage(), e);
        } catch (HttpStatusCodeException e) {
            throw wrapHttp(e, "upload document");
        } catch (RestClientException e) {
            log.error("LOS upload document failed for {}: {}", applicationId, e.getMessage());
            throw new LosIntegrationException("LOS request failed: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> exchange(HttpMethod method, String path, Object body) {
        if (!isConfigured()) {
            throw new LosIntegrationException(
                    "LOS anchor integration is not configured (plp.los.base-url / plp.los.integration-api-key)");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(INTEGRATION_KEY_HEADER, integrationApiKey);
        try {
            RestTemplate restTemplate = restTemplateBuilder.build();
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    trimSlash(losBaseUrl) + path,
                    method,
                    new HttpEntity<>(body, headers),
                    new ParameterizedTypeReference<>() {});
            return response.getBody();
        } catch (HttpStatusCodeException e) {
            throw wrapHttp(e, method + " " + path);
        } catch (RestClientException e) {
            log.error("LOS anchor integration call failed [{} {}]: {}", method, path, e.getMessage());
            throw new LosIntegrationException("LOS request failed: " + e.getMessage(), e);
        }
    }

    private List<Map<String, Object>> exchangeList(HttpMethod method, String path) {
        if (!isConfigured()) {
            throw new LosIntegrationException(
                    "LOS anchor integration is not configured (plp.los.base-url / plp.los.integration-api-key)");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set(INTEGRATION_KEY_HEADER, integrationApiKey);
        try {
            RestTemplate restTemplate = restTemplateBuilder.build();
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    trimSlash(losBaseUrl) + path,
                    method,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {});
            List<Map<String, Object>> body = response.getBody();
            return body != null ? body : Collections.emptyList();
        } catch (HttpStatusCodeException e) {
            throw wrapHttp(e, method + " " + path);
        } catch (RestClientException e) {
            log.error("LOS list call failed [{} {}]: {}", method, path, e.getMessage());
            throw new LosIntegrationException("LOS request failed: " + e.getMessage(), e);
        }
    }

    private static LosIntegrationException wrapHttp(HttpStatusCodeException e, String context) {
        String body = e.getResponseBodyAsString();
        String detail = body != null && !body.isBlank() ? body : e.getStatusText();
        log.error("LOS call failed [{}]: {} — {}", context, e.getStatusCode().value(), detail);
        return new LosIntegrationException(
                extractMessage(detail, e.getMessage()),
                e,
                e.getStatusCode().value());
    }

    private static String extractMessage(String body, String fallback) {
        if (body == null || body.isBlank()) {
            return fallback != null ? fallback : "LOS request failed";
        }
        // Prefer JSON {"message":"..."} / {"error":"..."} when present.
        String msg = extractJsonField(body, "message");
        if (msg == null) {
            msg = extractJsonField(body, "error");
        }
        if (msg != null && !msg.isBlank()) {
            return msg;
        }
        return body.length() > 400 ? body.substring(0, 400) : body;
    }

    private static String extractJsonField(String json, String field) {
        String key = "\"" + field + "\"";
        int i = json.indexOf(key);
        if (i < 0) {
            return null;
        }
        int colon = json.indexOf(':', i + key.length());
        if (colon < 0) {
            return null;
        }
        int startQuote = json.indexOf('"', colon + 1);
        if (startQuote < 0) {
            return null;
        }
        int endQuote = json.indexOf('"', startQuote + 1);
        if (endQuote < 0) {
            return null;
        }
        return json.substring(startQuote + 1, endQuote);
    }

    private static String trimSlash(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
