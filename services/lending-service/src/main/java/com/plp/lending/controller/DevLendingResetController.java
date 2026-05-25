package com.plp.lending.controller;

import com.plp.lending.audit.AuditHeaders;
import com.plp.lending.dev.DevLendingDataResetService;
import com.plp.lending.dev.DevResetGuard;
import com.plp.lending.security.LoanAccessGuard;
import com.plp.lending.service.LoanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static com.plp.lending.dev.DevResetGuard.requirePlatformAdminHeader;

@RestController
@RequestMapping("/api/v1/dev")
@RequiredArgsConstructor
public class DevLendingResetController {

    private final DevResetGuard devResetGuard;
    private final DevLendingDataResetService devLendingDataResetService;
    private final LoanService loanService;

    @PostMapping("/reset-lending-data")
    public ResponseEntity<Map<String, String>> resetLendingData(
            @RequestHeader(value = "X-User-Roles", required = false) String rolesHeader) {
        devResetGuard.requireDevResetEnabled();
        requirePlatformAdminHeader(rolesHeader);

        devLendingDataResetService.resetAllLendingData();

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Demo data reset completed"));
    }

    @PostMapping("/repair-loan-subprogram-links")
    public ResponseEntity<Map<String, Object>> repairLoanSubprogramLinks(
            @RequestHeader(value = AuditHeaders.X_USER_ID, required = false) String userIdHeader,
            @RequestHeader(value = LoanAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = LoanAccessGuard.HEADER_LINKED_ENTITY_ID, required = false)
                    String linkedEntityId,
            @RequestHeader(value = LoanAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false)
                    String linkedEntityType) {
        devResetGuard.requireDevResetEnabled();
        requirePlatformAdminHeader(rolesHeader);
        Map<String, Object> data =
                loanService.repairLoanSubProgramLinks(userIdHeader, rolesHeader, linkedEntityId, linkedEntityType);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "data", data));
    }
}
