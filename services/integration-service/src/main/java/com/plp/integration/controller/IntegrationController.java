package com.plp.integration.controller;

import com.plp.integration.adapter.erp.ErpSystemAdapter;
import com.plp.integration.adapter.hr.HrSystemAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/integrations")
@RequiredArgsConstructor
public class IntegrationController {

    private final HrSystemAdapter hrSystemAdapter;
    private final ErpSystemAdapter erpSystemAdapter;

    @GetMapping("/hr/salary")
    public ResponseEntity<Map<String, Object>> getSalaryInfo(
            @RequestParam String employeeId,
            @RequestParam UUID anchorId) {
        var salaryInfo = hrSystemAdapter.fetchSalaryInfo(employeeId, anchorId);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", salaryInfo));
    }

    @GetMapping("/hr/earned-salary")
    public ResponseEntity<Map<String, Object>> getEarnedSalary(
            @RequestParam String employeeId,
            @RequestParam UUID anchorId) {
        BigDecimal earned = hrSystemAdapter.getEarnedSalary(employeeId, anchorId, LocalDate.now());
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", Map.of("earnedSalary", earned)));
    }

    @GetMapping("/hr/employment")
    public ResponseEntity<Map<String, Object>> verifyEmployment(
            @RequestParam String employeeId,
            @RequestParam UUID anchorId) {
        var status = hrSystemAdapter.verifyEmployment(employeeId, anchorId);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", status));
    }

    @GetMapping("/erp/invoice/{invoiceNumber}")
    public ResponseEntity<Map<String, Object>> getInvoice(
            @PathVariable String invoiceNumber,
            @RequestParam UUID anchorId) {
        var invoice = erpSystemAdapter.fetchInvoice(invoiceNumber, anchorId);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", invoice));
    }

    @GetMapping("/erp/invoices")
    public ResponseEntity<Map<String, Object>> listPendingInvoices(
            @RequestParam String buyerId,
            @RequestParam UUID anchorId) {
        var invoices = erpSystemAdapter.listPendingInvoices(buyerId, anchorId);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", invoices));
    }

    @GetMapping("/erp/three-way-match/{invoiceNumber}")
    public ResponseEntity<Map<String, Object>> verifyThreeWayMatch(
            @PathVariable String invoiceNumber,
            @RequestParam UUID anchorId) {
        var result = erpSystemAdapter.verifyThreeWayMatch(invoiceNumber, anchorId);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", result));
    }
}
