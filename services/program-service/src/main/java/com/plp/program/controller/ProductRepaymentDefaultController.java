package com.plp.program.controller;

import com.plp.program.model.dto.ProductRepaymentDefaultDto;
import com.plp.program.model.dto.ProductRepaymentDefaultUpdateDto;
import com.plp.program.model.enums.ProductType;
import com.plp.program.security.LenderPortalRoleAuthorization;
import com.plp.program.service.ProductRepaymentDefaultService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/platform/repayment-defaults")
@RequiredArgsConstructor
public class ProductRepaymentDefaultController {

    private final ProductRepaymentDefaultService repaymentDefaultService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestHeader(value = LenderPortalRoleAuthorization.HEADER_USER_ROLES, required = false) String rolesHeader) {
        LenderPortalRoleAuthorization.requirePlatformAdmin(rolesHeader);
        List<ProductRepaymentDefaultDto> rows = repaymentDefaultService.listAll();
        return ResponseEntity.ok(Map.of("data", rows));
    }

    @GetMapping("/{productType}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable ProductType productType,
            @RequestHeader(value = LenderPortalRoleAuthorization.HEADER_USER_ROLES, required = false) String rolesHeader) {
        LenderPortalRoleAuthorization.requirePlatformAdmin(rolesHeader);
        return ResponseEntity.ok(Map.of("data", repaymentDefaultService.get(productType)));
    }

    @PutMapping("/{productType}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable ProductType productType,
            @RequestBody ProductRepaymentDefaultUpdateDto dto,
            @RequestHeader(value = LenderPortalRoleAuthorization.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        LenderPortalRoleAuthorization.requirePlatformAdmin(rolesHeader);
        ProductRepaymentDefaultDto saved = repaymentDefaultService.update(productType, dto, userIdHeader);
        return ResponseEntity.ok(Map.of("data", saved));
    }
}
