package com.plp.program.controller;

import com.plp.program.dev.DevProgramDataResetService;
import com.plp.program.dev.DevResetGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static com.plp.program.dev.DevResetGuard.requirePlatformAdminHeader;

@RestController
@RequestMapping("/api/v1/dev")
@RequiredArgsConstructor
public class DevProgramResetController {

    private final DevResetGuard devResetGuard;
    private final DevProgramDataResetService devProgramDataResetService;

    @PostMapping("/reset-program-data")
    public ResponseEntity<Map<String, String>> resetProgramData(
            @RequestHeader(value = "X-User-Roles", required = false) String rolesHeader) {
        devResetGuard.requireDevResetEnabled();
        requirePlatformAdminHeader(rolesHeader);

        devProgramDataResetService.resetAllProgramData();

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Demo data reset completed"));
    }
}
