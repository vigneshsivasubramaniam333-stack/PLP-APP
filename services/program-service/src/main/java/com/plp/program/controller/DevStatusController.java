package com.plp.program.controller;

import com.plp.program.dev.DevResetGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/dev")
@RequiredArgsConstructor
public class DevStatusController {

    private final DevResetGuard devResetGuard;

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("devResetEnabled", devResetGuard.isDevResetEnabled());
        body.put("profile", devResetGuard.getActiveProfilesDisplay());
        return ResponseEntity.ok(body);
    }
}
