package com.plp.lending.controller.integration;

import com.plp.lending.service.integration.LosBorrowerLendingCleanupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/integrations/los")
@RequiredArgsConstructor
public class LosBorrowerLendingCleanupController {

    private final LosBorrowerLendingCleanupService cleanupService;

    @PostMapping("/borrowers/{borrowerId}/cleanup")
    public ResponseEntity<Map<String, Object>> cleanupBorrower(@PathVariable UUID borrowerId) {
        int loansRemoved = cleanupService.cleanupBorrower(borrowerId);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "data", Map.of("loansRemoved", loansRemoved)));
    }
}
