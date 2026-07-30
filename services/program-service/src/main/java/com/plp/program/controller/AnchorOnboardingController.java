package com.plp.program.controller;

import com.plp.program.service.AnchorOnboardingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Anchor onboarding portal (Phase 2): status summary + delegated LOS application read/write and
 * submit/resubmit, proxied to los-core-service via {@link AnchorOnboardingService}.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/portal/anchor/onboarding")
@RequiredArgsConstructor
public class AnchorOnboardingController {

    private final AnchorOnboardingService anchorOnboardingService;

    private UUID requireAnchorFromHeaders(String linkedEntityType, String linkedEntityId) {
        if (linkedEntityType == null || linkedEntityType.isBlank()
                || !"ANCHOR".equalsIgnoreCase(linkedEntityType.trim())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Anchor onboarding requires linkedEntityType ANCHOR");
        }
        if (linkedEntityId == null || linkedEntityId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing X-Linked-Entity-Id");
        }
        try {
            return UUID.fromString(linkedEntityId.trim());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid X-Linked-Entity-Id");
        }
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> summary(
            @RequestHeader(value = "X-Linked-Entity-Type", required = false) String linkedEntityType,
            @RequestHeader(value = "X-Linked-Entity-Id", required = false) String linkedEntityId) {
        UUID anchorId = requireAnchorFromHeaders(linkedEntityType, linkedEntityId);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", anchorOnboardingService.getSummary(anchorId)));
    }

    @GetMapping("/application")
    public ResponseEntity<Map<String, Object>> getApplication(
            @RequestHeader(value = "X-Linked-Entity-Type", required = false) String linkedEntityType,
            @RequestHeader(value = "X-Linked-Entity-Id", required = false) String linkedEntityId) {
        UUID anchorId = requireAnchorFromHeaders(linkedEntityType, linkedEntityId);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", anchorOnboardingService.getApplication(anchorId)));
    }

    @GetMapping("/intake-context")
    public ResponseEntity<Map<String, Object>> intakeContext(
            @RequestHeader(value = "X-Linked-Entity-Type", required = false) String linkedEntityType,
            @RequestHeader(value = "X-Linked-Entity-Id", required = false) String linkedEntityId) {
        UUID anchorId = requireAnchorFromHeaders(linkedEntityType, linkedEntityId);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", anchorOnboardingService.getIntakeContext(anchorId)));
    }

    @GetMapping("/geo/states")
    public ResponseEntity<Map<String, Object>> listStates(
            @RequestHeader(value = "X-Linked-Entity-Type", required = false) String linkedEntityType,
            @RequestHeader(value = "X-Linked-Entity-Id", required = false) String linkedEntityId) {
        requireAnchorFromHeaders(linkedEntityType, linkedEntityId);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", anchorOnboardingService.listStates()));
    }

    @GetMapping("/geo/states/{stateId}/cities")
    public ResponseEntity<Map<String, Object>> listCities(
            @PathVariable UUID stateId,
            @RequestHeader(value = "X-Linked-Entity-Type", required = false) String linkedEntityType,
            @RequestHeader(value = "X-Linked-Entity-Id", required = false) String linkedEntityId) {
        requireAnchorFromHeaders(linkedEntityType, linkedEntityId);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", anchorOnboardingService.listCities(stateId)));
    }

    @PutMapping("/application")
    public ResponseEntity<Map<String, Object>> updateApplication(
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "X-Linked-Entity-Type", required = false) String linkedEntityType,
            @RequestHeader(value = "X-Linked-Entity-Id", required = false) String linkedEntityId) {
        UUID anchorId = requireAnchorFromHeaders(linkedEntityType, linkedEntityId);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "data", anchorOnboardingService.updateApplication(anchorId, payload)));
    }

    @PostMapping("/submit")
    public ResponseEntity<Map<String, Object>> submit(
            @RequestHeader(value = "X-Linked-Entity-Type", required = false) String linkedEntityType,
            @RequestHeader(value = "X-Linked-Entity-Id", required = false) String linkedEntityId) {
        UUID anchorId = requireAnchorFromHeaders(linkedEntityType, linkedEntityId);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", anchorOnboardingService.submit(anchorId)));
    }

    @PostMapping("/resubmit")
    public ResponseEntity<Map<String, Object>> resubmit(
            @RequestHeader(value = "X-Linked-Entity-Type", required = false) String linkedEntityType,
            @RequestHeader(value = "X-Linked-Entity-Id", required = false) String linkedEntityId) {
        UUID anchorId = requireAnchorFromHeaders(linkedEntityType, linkedEntityId);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", anchorOnboardingService.resubmit(anchorId)));
    }

    @GetMapping("/documents")
    public ResponseEntity<Map<String, Object>> listDocuments(
            @RequestHeader(value = "X-Linked-Entity-Type", required = false) String linkedEntityType,
            @RequestHeader(value = "X-Linked-Entity-Id", required = false) String linkedEntityId) {
        UUID anchorId = requireAnchorFromHeaders(linkedEntityType, linkedEntityId);
        List<Map<String, Object>> docs = anchorOnboardingService.listDocuments(anchorId);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", docs));
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @RequestParam String documentType,
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-Linked-Entity-Type", required = false) String linkedEntityType,
            @RequestHeader(value = "X-Linked-Entity-Id", required = false) String linkedEntityId) {
        UUID anchorId = requireAnchorFromHeaders(linkedEntityType, linkedEntityId);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "data", anchorOnboardingService.uploadDocument(anchorId, documentType, file)));
    }

    @GetMapping("/documents/{documentId}/download")
    public ResponseEntity<byte[]> downloadDocument(
            @PathVariable UUID documentId,
            @RequestHeader(value = "X-Linked-Entity-Type", required = false) String linkedEntityType,
            @RequestHeader(value = "X-Linked-Entity-Id", required = false) String linkedEntityId) {
        UUID anchorId = requireAnchorFromHeaders(linkedEntityType, linkedEntityId);
        return anchorOnboardingService.streamDocument(anchorId, documentId, false);
    }

    @GetMapping("/documents/{documentId}/content")
    public ResponseEntity<byte[]> previewDocument(
            @PathVariable UUID documentId,
            @RequestHeader(value = "X-Linked-Entity-Type", required = false) String linkedEntityType,
            @RequestHeader(value = "X-Linked-Entity-Id", required = false) String linkedEntityId) {
        UUID anchorId = requireAnchorFromHeaders(linkedEntityType, linkedEntityId);
        return anchorOnboardingService.streamDocument(anchorId, documentId, true);
    }
}
