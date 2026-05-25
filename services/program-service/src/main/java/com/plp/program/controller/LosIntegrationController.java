package com.plp.program.controller;

import com.plp.program.model.dto.integration.*;
import com.plp.program.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/integrations/los")
@RequiredArgsConstructor
public class LosIntegrationController {

    private final LosIntegrationService losIntegrationService;
    private final LosAnchorIntegrationService losAnchorIntegrationService;
    private final LosProgramIntegrationService losProgramIntegrationService;
    private final LosSubProgramIntegrationService losSubProgramIntegrationService;
    private final LosBorrowerIntegrationService losBorrowerIntegrationService;
    private final LosSubProgramBorrowerLinkIntegrationService losSubProgramBorrowerLinkIntegrationService;
    private final LosBorrowerProgramMappingIntegrationService losBorrowerProgramMappingIntegrationService;

    @PostMapping("/program-borrower-link")
    public ResponseEntity<Map<String, Object>> linkProgramBorrower(@Valid @RequestBody LosProgramBorrowerLinkRequest body) {
        LosProgramBorrowerLinkResponse data = losIntegrationService.linkProgramBorrower(body);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", data));
    }

    @PostMapping("/anchors")
    public ResponseEntity<Map<String, Object>> syncAnchor(@Valid @RequestBody LosAnchorSyncRequest body) {
        LosAnchorSyncResponse data = losAnchorIntegrationService.syncAnchor(body);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", data));
    }

    @PostMapping("/programs")
    public ResponseEntity<Map<String, Object>> upsertProgram(@Valid @RequestBody LosProgramUpsertRequest body) {
        LosProgramUpsertResponse data = losProgramIntegrationService.upsert(body);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", data));
    }

    @PostMapping("/sub-programs")
    public ResponseEntity<Map<String, Object>> upsertSubProgram(@Valid @RequestBody LosSubProgramUpsertRequest body) {
        LosSubProgramUpsertResponse data = losSubProgramIntegrationService.upsert(body);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", data));
    }

    @PostMapping("/borrowers")
    public ResponseEntity<Map<String, Object>> upsertBorrower(@Valid @RequestBody LosBorrowerUpsertRequest body) {
        LosBorrowerUpsertResponse data = losBorrowerIntegrationService.upsert(body);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", data));
    }

    @PostMapping("/sub-program-borrower-links")
    public ResponseEntity<Map<String, Object>> linkSubProgramBorrower(
            @Valid @RequestBody LosSubProgramBorrowerLinkRequest body) {
        LosSubProgramBorrowerLinkResponse data = losSubProgramBorrowerLinkIntegrationService.link(body);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", data));
    }

    @PostMapping("/borrower-program-mappings")
    public ResponseEntity<Map<String, Object>> upsertBorrowerProgramMapping(
            @Valid @RequestBody LosBorrowerProgramMappingUpsertRequest body) {
        LosBorrowerProgramMappingUpsertResponse data = losBorrowerProgramMappingIntegrationService.upsert(body);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", data));
    }
}
