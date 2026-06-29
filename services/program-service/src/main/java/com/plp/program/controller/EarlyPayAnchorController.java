package com.plp.program.controller;

import com.plp.program.model.dto.EarlyPayBorrowerRowDto;
import com.plp.program.model.dto.EarlyPayCreateParameterRequest;
import com.plp.program.model.dto.EarlyPayRepaymentUploadResult;
import com.plp.program.model.entity.EarlyPayParameter;
import com.plp.program.model.entity.EarlyPayRequest;
import com.plp.program.model.entity.Invoice;
import com.plp.program.model.entity.SubProgram;
import com.plp.program.service.earlypay.EarlyPayService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/portal/anchor/early-pay")
@RequiredArgsConstructor
public class EarlyPayAnchorController {

    private final EarlyPayService earlyPayService;

    private UUID requireAnchor(String linkedEntityType, String linkedEntityId) {
        if (linkedEntityType == null || !"ANCHOR".equalsIgnoreCase(linkedEntityType.trim())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Anchor portal required");
        }
        try {
            return UUID.fromString(linkedEntityId.trim());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid anchor id");
        }
    }

    @GetMapping("/enabled")
    public ResponseEntity<Map<String, Object>> isEnabled(
            @RequestHeader(value = "X-Linked-Entity-Type", required = false) String linkedEntityType,
            @RequestHeader(value = "X-Linked-Entity-Id", required = false) String linkedEntityId) {
        UUID anchorId = requireAnchor(linkedEntityType, linkedEntityId);
        boolean enabled = earlyPayService.anchorHasEarlyPayEnabled(anchorId);
        List<SubProgram> subs = earlyPayService.listEarlyPaySubPrograms(anchorId);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "enabled", enabled,
                "subPrograms", subs));
    }

    @GetMapping("/sub-programs")
    public ResponseEntity<Map<String, Object>> listSubPrograms(
            @RequestHeader(value = "X-Linked-Entity-Type", required = false) String linkedEntityType,
            @RequestHeader(value = "X-Linked-Entity-Id", required = false) String linkedEntityId) {
        UUID anchorId = requireAnchor(linkedEntityType, linkedEntityId);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "data", earlyPayService.listEarlyPaySubPrograms(anchorId)));
    }

    @GetMapping("/parameters")
    public ResponseEntity<Map<String, Object>> listParameters(
            @RequestParam(required = false) UUID subProgramId,
            @RequestHeader(value = "X-Linked-Entity-Type", required = false) String linkedEntityType,
            @RequestHeader(value = "X-Linked-Entity-Id", required = false) String linkedEntityId) {
        UUID anchorId = requireAnchor(linkedEntityType, linkedEntityId);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "data", earlyPayService.listParameters(anchorId, subProgramId)));
    }

    @PostMapping("/parameters")
    public ResponseEntity<Map<String, Object>> createParameter(
            @RequestBody EarlyPayCreateParameterRequest body,
            @RequestHeader(value = "X-Linked-Entity-Type", required = false) String linkedEntityType,
            @RequestHeader(value = "X-Linked-Entity-Id", required = false) String linkedEntityId) {
        UUID anchorId = requireAnchor(linkedEntityType, linkedEntityId);
        try {
            EarlyPayParameter created = earlyPayService.createParameter(anchorId, body);
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", created));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @GetMapping("/borrowers")
    public ResponseEntity<Map<String, Object>> listBorrowers(
            @RequestParam UUID subProgramId,
            @RequestHeader(value = "X-Linked-Entity-Type", required = false) String linkedEntityType,
            @RequestHeader(value = "X-Linked-Entity-Id", required = false) String linkedEntityId) {
        UUID anchorId = requireAnchor(linkedEntityType, linkedEntityId);
        List<EarlyPayBorrowerRowDto> rows = earlyPayService.listBorrowersForEnablement(anchorId, subProgramId);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", rows));
    }

    @PutMapping("/borrowers/{membershipId}")
    public ResponseEntity<Map<String, Object>> updateBorrower(
            @PathVariable UUID membershipId,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-Linked-Entity-Type", required = false) String linkedEntityType,
            @RequestHeader(value = "X-Linked-Entity-Id", required = false) String linkedEntityId) {
        UUID anchorId = requireAnchor(linkedEntityType, linkedEntityId);
        String enable = body != null ? body.get("enableEarlyPay") : "NO";
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "data", earlyPayService.updateBorrowerEnablement(anchorId, membershipId, enable)));
    }

    @GetMapping("/requests")
    public ResponseEntity<Map<String, Object>> listRequests(
            @RequestParam(defaultValue = "REQUESTED") String status,
            @RequestParam(required = false) UUID subProgramId,
            @RequestHeader(value = "X-Linked-Entity-Type", required = false) String linkedEntityType,
            @RequestHeader(value = "X-Linked-Entity-Id", required = false) String linkedEntityId) {
        UUID anchorId = requireAnchor(linkedEntityType, linkedEntityId);
        List<EarlyPayRequest> requests = earlyPayService.listRequests(anchorId, subProgramId, status);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", requests));
    }

    @PostMapping("/requests/approve")
    public ResponseEntity<Map<String, Object>> approveRequests(
            @RequestBody Map<String, List<UUID>> body,
            @RequestHeader(value = "X-Linked-Entity-Type", required = false) String linkedEntityType,
            @RequestHeader(value = "X-Linked-Entity-Id", required = false) String linkedEntityId) {
        UUID anchorId = requireAnchor(linkedEntityType, linkedEntityId);
        List<UUID> ids = body != null && body.get("requestIds") != null ? body.get("requestIds") : List.of();
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "data", earlyPayService.approveRequests(anchorId, ids)));
    }

    @PostMapping("/requests/reject")
    public ResponseEntity<Map<String, Object>> rejectRequests(
            @RequestBody Map<String, List<UUID>> body,
            @RequestHeader(value = "X-Linked-Entity-Type", required = false) String linkedEntityType,
            @RequestHeader(value = "X-Linked-Entity-Id", required = false) String linkedEntityId) {
        UUID anchorId = requireAnchor(linkedEntityType, linkedEntityId);
        List<UUID> ids = body != null && body.get("requestIds") != null ? body.get("requestIds") : List.of();
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "data", earlyPayService.rejectRequests(anchorId, ids)));
    }

    @GetMapping("/payments")
    public ResponseEntity<Map<String, Object>> listPayments(
            @RequestHeader(value = "X-Linked-Entity-Type", required = false) String linkedEntityType,
            @RequestHeader(value = "X-Linked-Entity-Id", required = false) String linkedEntityId) {
        UUID anchorId = requireAnchor(linkedEntityType, linkedEntityId);
        List<Invoice> invoices = earlyPayService.listPaymentInvoices(anchorId);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", invoices));
    }

    @GetMapping("/closed")
    public ResponseEntity<Map<String, Object>> listClosed(
            @RequestHeader(value = "X-Linked-Entity-Type", required = false) String linkedEntityType,
            @RequestHeader(value = "X-Linked-Entity-Id", required = false) String linkedEntityId) {
        UUID anchorId = requireAnchor(linkedEntityType, linkedEntityId);
        List<Invoice> invoices = earlyPayService.listClosedEpInvoices(anchorId);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", invoices));
    }

    @GetMapping("/repayments")
    public ResponseEntity<Map<String, Object>> listRepayments(
            @RequestHeader(value = "X-Linked-Entity-Type", required = false) String linkedEntityType,
            @RequestHeader(value = "X-Linked-Entity-Id", required = false) String linkedEntityId) {
        UUID anchorId = requireAnchor(linkedEntityType, linkedEntityId);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", earlyPayService.listRepayments(anchorId)));
    }

    @PostMapping(value = "/repayments/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadRepayments(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-Linked-Entity-Type", required = false) String linkedEntityType,
            @RequestHeader(value = "X-Linked-Entity-Id", required = false) String linkedEntityId) {
        UUID anchorId = requireAnchor(linkedEntityType, linkedEntityId);
        EarlyPayRepaymentUploadResult result = earlyPayService.uploadRepayments(anchorId, file);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", result));
    }
}
