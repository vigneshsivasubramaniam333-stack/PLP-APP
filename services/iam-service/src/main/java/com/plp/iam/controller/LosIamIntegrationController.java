package com.plp.iam.controller;

import com.plp.iam.model.dto.LosProvisionUserRequest;
import com.plp.iam.model.dto.LosProvisionUserResponse;
import com.plp.iam.service.LosIamIntegrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/integrations/los")
@RequiredArgsConstructor
public class LosIamIntegrationController {

    public static final String INTEGRATION_API_KEY_HEADER = "X-Los-Integration-Key";

    private final LosIamIntegrationService losIamIntegrationService;

    @Value("${plp.los-integration.api-key:}")
    private String integrationApiKey;

    @PostMapping("/users")
    public ResponseEntity<Map<String, Object>> provisionUser(
            @RequestHeader(value = INTEGRATION_API_KEY_HEADER, required = false) String apiKey,
            @Valid @RequestBody LosProvisionUserRequest request) {
        assertIntegrationKey(apiKey);
        LosProvisionUserResponse data = losIamIntegrationService.provisionUser(request);
        return ResponseEntity.status(data.isCreated() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(Map.of("status", "SUCCESS", "data", data));
    }

    private void assertIntegrationKey(String apiKey) {
        if (integrationApiKey == null || integrationApiKey.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "LOS integration API key is not configured");
        }
        if (apiKey == null || !integrationApiKey.equals(apiKey)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid LOS integration API key");
        }
    }
}
