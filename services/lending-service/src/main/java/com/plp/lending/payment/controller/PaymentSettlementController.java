package com.plp.lending.payment.controller;

import com.plp.lending.payment.model.PaymentInProgress;
import com.plp.lending.payment.model.PaymentTransaction;
import com.plp.lending.payment.model.PgSettlementBatch;
import com.plp.lending.payment.service.PaymentSettlementService;
import com.plp.lending.security.LenderRoleAuthorization;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments/settlements")
@RequiredArgsConstructor
public class PaymentSettlementController {

    private final PaymentSettlementService settlementService;

    @GetMapping("/pip")
    public ResponseEntity<Map<String, Object>> listOpenPip(
            @RequestHeader(value = LenderRoleAuthorization.HEADER_USER_ROLES, required = false) String rolesHeader) {
        LenderRoleAuthorization.requireRepayRoles(rolesHeader);
        List<PaymentInProgress> rows = settlementService.listOpenPip();
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", rows));
    }

    @GetMapping("/transactions")
    public ResponseEntity<Map<String, Object>> listTransactions(
            @RequestHeader(value = LenderRoleAuthorization.HEADER_USER_ROLES, required = false) String rolesHeader) {
        LenderRoleAuthorization.requireRepayRoles(rolesHeader);
        List<PaymentTransaction> rows = settlementService.listSuccessfulTransactions();
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", rows));
    }

    @PostMapping("/batches")
    public ResponseEntity<Map<String, Object>> createBatch(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = LenderRoleAuthorization.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        LenderRoleAuthorization.requireRepayRoles(rolesHeader);
        LocalDate settlementDate = LocalDate.parse(body.get("settlementDate").toString());
        String utr = body.get("settlementUtr").toString();
        @SuppressWarnings("unchecked")
        List<String> rawPipIds = (List<String>) body.get("pipIds");
        List<UUID> pipIds = rawPipIds.stream().map(UUID::fromString).toList();
        String remarks = body.get("remarks") != null ? body.get("remarks").toString() : null;
        PgSettlementBatch batch = settlementService.createAndApplyBatch(
                settlementDate, utr, pipIds, userId, remarks);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", batch));
    }
}
