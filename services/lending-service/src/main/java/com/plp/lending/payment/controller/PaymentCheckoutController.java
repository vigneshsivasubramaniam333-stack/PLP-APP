package com.plp.lending.payment.controller;

import com.plp.lending.payment.model.PaymentCheckoutLine;
import com.plp.lending.payment.service.PaymentCheckoutService;
import com.plp.lending.security.LoanAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/portal/borrower/payments/checkout")
@RequiredArgsConstructor
public class PaymentCheckoutController {

    private final PaymentCheckoutService checkoutService;

    @GetMapping("/lines")
    public ResponseEntity<Map<String, Object>> listLines(
            @RequestParam(required = false) UUID borrowerId,
            @RequestHeader(value = LoanAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = LoanAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = LoanAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        UUID resolved = PaymentAccessResolver.resolveBorrowerId(
                borrowerId, rolesHeader, linkedEntityId, linkedEntityType, userId);
        List<PaymentCheckoutLine> lines = checkoutService.listCart(resolved);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", lines));
    }

    @GetMapping("/count")
    public ResponseEntity<Map<String, Object>> count(
            @RequestParam(required = false) UUID borrowerId,
            @RequestHeader(value = LoanAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = LoanAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = LoanAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        UUID resolved = PaymentAccessResolver.resolveBorrowerId(
                borrowerId, rolesHeader, linkedEntityId, linkedEntityType, userId);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", checkoutService.cartCount(resolved)));
    }

    @GetMapping("/payment-method")
    public ResponseEntity<Map<String, Object>> paymentMethod(
            @RequestParam(required = false) UUID borrowerId,
            @RequestHeader(value = LoanAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = LoanAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = LoanAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        UUID resolved = PaymentAccessResolver.resolveBorrowerId(
                borrowerId, rolesHeader, linkedEntityId, linkedEntityType, userId);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "data", Map.of("paymentMethod", checkoutService.resolvePaymentMethodForBorrower(resolved))));
    }

    @PostMapping("/lines")
    public ResponseEntity<Map<String, Object>> addLine(
            @RequestBody Map<String, Object> body,
            @RequestParam(required = false) UUID borrowerId,
            @RequestHeader(value = LoanAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = LoanAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = LoanAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        UUID resolved = PaymentAccessResolver.resolveBorrowerId(
                borrowerId, rolesHeader, linkedEntityId, linkedEntityType, userId);
        UUID invoiceId = UUID.fromString(body.get("invoiceId").toString());
        BigDecimal amount = body.get("amount") != null ? new BigDecimal(body.get("amount").toString()) : null;
        PaymentCheckoutLine line = checkoutService.addInvoiceToCart(resolved, invoiceId, amount);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", line));
    }

    @PostMapping("/lines/bulk")
    public ResponseEntity<Map<String, Object>> addBulk(
            @RequestBody Map<String, Object> body,
            @RequestParam(required = false) UUID borrowerId,
            @RequestHeader(value = LoanAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = LoanAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = LoanAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        UUID resolved = PaymentAccessResolver.resolveBorrowerId(
                borrowerId, rolesHeader, linkedEntityId, linkedEntityType, userId);
        @SuppressWarnings("unchecked")
        List<String> rawIds = (List<String>) body.get("invoiceIds");
        List<UUID> invoiceIds = rawIds.stream().map(UUID::fromString).toList();
        List<PaymentCheckoutLine> lines = checkoutService.addBulk(resolved, invoiceIds);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", lines));
    }

    @DeleteMapping("/lines/{lineId}")
    public ResponseEntity<Map<String, Object>> removeLine(
            @PathVariable UUID lineId,
            @RequestParam(required = false) UUID borrowerId,
            @RequestHeader(value = LoanAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = LoanAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = LoanAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        UUID resolved = PaymentAccessResolver.resolveBorrowerId(
                borrowerId, rolesHeader, linkedEntityId, linkedEntityType, userId);
        checkoutService.removeLine(resolved, lineId);
        return ResponseEntity.ok(Map.of("status", "SUCCESS"));
    }

    @DeleteMapping("/lines")
    public ResponseEntity<Map<String, Object>> clearCart(
            @RequestParam(required = false) UUID borrowerId,
            @RequestHeader(value = LoanAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = LoanAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = LoanAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        UUID resolved = PaymentAccessResolver.resolveBorrowerId(
                borrowerId, rolesHeader, linkedEntityId, linkedEntityType, userId);
        checkoutService.clearCart(resolved);
        return ResponseEntity.ok(Map.of("status", "SUCCESS"));
    }
}
