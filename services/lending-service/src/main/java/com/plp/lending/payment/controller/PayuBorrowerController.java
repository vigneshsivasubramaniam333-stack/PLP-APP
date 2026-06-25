package com.plp.lending.payment.controller;

import com.plp.lending.payment.service.PaymentCheckoutService;
import com.plp.lending.security.LoanAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/portal/borrower/payments/payu")
@RequiredArgsConstructor
public class PayuBorrowerController {

    private final PaymentCheckoutService checkoutService;

    @PostMapping("/initiate")
    public ResponseEntity<Map<String, Object>> initiate(
            @RequestBody(required = false) Map<String, Object> body,
            @RequestParam(required = false) UUID borrowerId,
            @RequestHeader(value = LoanAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = LoanAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = LoanAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        UUID resolved = PaymentAccessResolver.resolveBorrowerId(
                borrowerId, rolesHeader, linkedEntityId, linkedEntityType, userId);
        String portalSource = body != null && body.get("portalSource") != null
                ? body.get("portalSource").toString()
                : "PLP";
        Map<String, Object> payload = checkoutService.initiatePayu(resolved, portalSource);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", payload));
    }
}
