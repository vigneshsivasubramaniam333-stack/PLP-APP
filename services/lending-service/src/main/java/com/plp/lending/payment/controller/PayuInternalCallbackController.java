package com.plp.lending.payment.controller;

import com.plp.lending.payment.service.PaymentCheckoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Internal callbacks invoked by integration-service after PayU browser redirect. */
@RestController
@RequestMapping("/api/v1/internal/payments/payu")
@RequiredArgsConstructor
@Slf4j
public class PayuInternalCallbackController {

    private final PaymentCheckoutService checkoutService;

    @PostMapping("/success")
    public Map<String, String> success(@RequestParam Map<String, String> params) {
        try {
            String url = checkoutService.handlePayuSuccess(params);
            return Map.of("redirectUrl", url);
        } catch (Exception e) {
            log.error("PayU success callback failed for txn {}: {}", params.get("txnid"), e.getMessage(), e);
            String url = checkoutService.handlePayuFailure(params);
            return Map.of("redirectUrl", url);
        }
    }

    @PostMapping("/failure")
    public Map<String, String> failure(@RequestParam Map<String, String> params) {
        String url = checkoutService.handlePayuFailure(params);
        return Map.of("redirectUrl", url);
    }
}
