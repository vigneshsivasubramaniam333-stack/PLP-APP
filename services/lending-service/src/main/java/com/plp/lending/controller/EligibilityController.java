package com.plp.lending.controller;

import com.plp.lending.service.EligibilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/loans/eligibility")
@RequiredArgsConstructor
public class EligibilityController {

    private final EligibilityService eligibilityService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> checkEligibility(
            @RequestParam UUID borrowerId,
            @RequestParam UUID programId,
            @RequestParam BigDecimal requestedAmount,
            @RequestParam(required = false) UUID salaryDataId) {
        Map<String, Object> result =
                eligibilityService.checkPayDayLoanEligibility(borrowerId, programId, requestedAmount, salaryDataId);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", result));
    }
}
