package com.plp.program.controller;

import com.plp.program.model.dto.InvoiceDigitalAttachmentResult;
import com.plp.program.model.entity.Borrower;
import com.plp.program.model.entity.EmployeeSalaryData;
import com.plp.program.model.entity.Invoice;
import com.plp.program.model.entity.Program;
import com.plp.program.repository.BorrowerRepository;
import com.plp.program.repository.ProgramRepository;
import com.plp.program.repository.SubProgramRepository;
import com.plp.program.security.AnchorPortalInvoiceAuth;
import com.plp.program.service.InvoiceService;
import com.plp.program.service.SalaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/portal/anchor")
@RequiredArgsConstructor
public class AnchorPortalController {

    private final BorrowerRepository borrowerRepository;
    private final ProgramRepository programRepository;
    private final SubProgramRepository subProgramRepository;
    private final SalaryService salaryService;
    private final InvoiceService invoiceService;

    /**
     * Resolves anchor id from trusted gateway headers. Optional {@code queryAnchorId} must match when supplied.
     */
    private UUID requireAnchorFromHeaders(
            String linkedEntityType,
            String linkedEntityId,
            UUID queryAnchorId,
            String userId) {
        if (linkedEntityType == null || linkedEntityType.isBlank()
                || !"ANCHOR".equalsIgnoreCase(linkedEntityType.trim())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Anchor portal data requires linkedEntityType ANCHOR");
        }
        if (linkedEntityId == null || linkedEntityId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing X-Linked-Entity-Id");
        }
        final UUID anchorId;
        try {
            anchorId = UUID.fromString(linkedEntityId.trim());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid X-Linked-Entity-Id");
        }
        if (queryAnchorId != null && !queryAnchorId.equals(anchorId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "anchorId does not match authenticated anchor");
        }
        log.info("Anchor scoped request: anchorId={} userId={}", anchorId, userId != null ? userId : "");
        return anchorId;
    }

    private void requireProgramBelongsToAnchor(UUID programId, UUID anchorIdUser) {
        Program program = programRepository.findById(programId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Program not found"));
        if (program.getAnchorId() != null) {
            if (!anchorIdUser.equals(program.getAnchorId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Program does not belong to this anchor");
            }
            return;
        }
        if (subProgramRepository.existsByProgramIdAndAnchorId(programId, anchorIdUser)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Program does not belong to this anchor");
    }

    private void requireInvoiceTenant(UUID invoiceAnchorId, UUID resolvedAnchorId) {
        if (invoiceAnchorId == null || !resolvedAnchorId.equals(invoiceAnchorId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invoice does not belong to this anchor");
        }
    }

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> dashboard(
            @RequestHeader(value = "X-Linked-Entity-Type", required = false) String linkedEntityType,
            @RequestHeader(value = "X-Linked-Entity-Id", required = false) String linkedEntityId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        UUID anchorId = requireAnchorFromHeaders(linkedEntityType, linkedEntityId, null, userId);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", Map.of(
                "message", "Anchor portal dashboard",
                "userId", userId != null ? userId : "",
                "anchorId", anchorId.toString()
        )));
    }

    @GetMapping("/programs")
    public ResponseEntity<Map<String, Object>> getAnchorPrograms(
            @RequestParam(required = false) UUID anchorId,
            @RequestHeader(value = "X-Linked-Entity-Type", required = false) String linkedEntityType,
            @RequestHeader(value = "X-Linked-Entity-Id", required = false) String linkedEntityId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        UUID resolvedAnchorId = requireAnchorFromHeaders(linkedEntityType, linkedEntityId, anchorId, userId);
        List<Program> programs = programRepository.findProgramsForAnchor(resolvedAnchorId);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", programs));
    }

    @GetMapping("/employees")
    public ResponseEntity<Map<String, Object>> getEmployees(
            @RequestParam(required = false) UUID anchorId,
            @RequestParam(required = false) UUID programId,
            @RequestHeader(value = "X-Linked-Entity-Type", required = false) String linkedEntityType,
            @RequestHeader(value = "X-Linked-Entity-Id", required = false) String linkedEntityId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        UUID resolvedAnchorId = requireAnchorFromHeaders(linkedEntityType, linkedEntityId, anchorId, userId);
        if (programId != null) {
            requireProgramBelongsToAnchor(programId, resolvedAnchorId);
        }
        List<Borrower> employees;
        if (programId != null) {
            employees = borrowerRepository.findByProgramId(programId);
        } else {
            employees = borrowerRepository.findByAnchorId(resolvedAnchorId);
        }
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", employees));
    }

    @GetMapping("/salary")
    public ResponseEntity<Map<String, Object>> getSalaryData(
            @RequestParam(required = false) UUID anchorId,
            @RequestParam(required = false) String payPeriod,
            @RequestHeader(value = "X-Linked-Entity-Type", required = false) String linkedEntityType,
            @RequestHeader(value = "X-Linked-Entity-Id", required = false) String linkedEntityId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        UUID resolvedAnchorId = requireAnchorFromHeaders(linkedEntityType, linkedEntityId, anchorId, userId);
        List<EmployeeSalaryData> data;
        if (payPeriod != null && !payPeriod.isBlank()) {
            data = salaryService.getByAnchorAndPeriod(resolvedAnchorId, payPeriod.trim());
        } else {
            data = salaryService.getAllByAnchorIdOrdered(resolvedAnchorId);
        }
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", data));
    }

    @PostMapping(value = "/salary/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadSalary(
            @RequestParam UUID anchorId,
            @RequestParam UUID programId,
            @RequestParam String payPeriod,
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-Linked-Entity-Type", required = false) String linkedEntityType,
            @RequestHeader(value = "X-Linked-Entity-Id", required = false) String linkedEntityId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        try {
            UUID resolvedAnchorId = requireAnchorFromHeaders(linkedEntityType, linkedEntityId, anchorId, userId);
            requireProgramBelongsToAnchor(programId, resolvedAnchorId);
            UUID uploadedBy = userId != null ? UUID.fromString(userId) : null;
            List<EmployeeSalaryData> results = salaryService.uploadSalaryCsv(
                    resolvedAnchorId, programId, payPeriod, file.getInputStream(), uploadedBy);
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "data", Map.of("rowsProcessed", results.size(), "records", results)));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    // --- Invoice Discounting endpoints ---

    @GetMapping("/invoices")
    public ResponseEntity<Map<String, Object>> getInvoices(
            @RequestParam(required = false) UUID anchorId,
            @RequestParam(required = false) UUID programId,
            @RequestHeader(value = "X-Linked-Entity-Type", required = false) String linkedEntityType,
            @RequestHeader(value = "X-Linked-Entity-Id", required = false) String linkedEntityId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        UUID resolvedAnchorId = requireAnchorFromHeaders(linkedEntityType, linkedEntityId, anchorId, userId);
        if (programId != null) {
            requireProgramBelongsToAnchor(programId, resolvedAnchorId);
        }
        List<Invoice> invoices;
        if (programId != null) {
            invoices = invoiceService.getByAnchorAndProgram(resolvedAnchorId, programId);
        } else {
            invoices = invoiceService.getByAnchor(resolvedAnchorId);
        }
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", invoices));
    }

    @PostMapping("/invoices")
    public ResponseEntity<Map<String, Object>> createInvoice(
            @RequestBody Invoice invoice,
            @RequestHeader(value = "X-User-Roles", required = false) String roles,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Linked-Entity-Type", required = false) String linkedEntityType,
            @RequestHeader(value = "X-Linked-Entity-Id", required = false) String linkedEntityId) {
        AnchorPortalInvoiceAuth.requireInvoiceUploadRole(roles);
        try {
            UUID resolvedAnchorId = requireAnchorFromHeaders(linkedEntityType, linkedEntityId, null, userId);
            if (invoice.getAnchorId() == null) {
                invoice.setAnchorId(resolvedAnchorId);
            } else if (!resolvedAnchorId.equals(invoice.getAnchorId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "invoice anchorId does not match authenticated anchor");
            }
            requireProgramBelongsToAnchor(invoice.getProgramId(), resolvedAnchorId);
            UUID uploadedByUserId = userId != null ? UUID.fromString(userId) : null;
            Invoice created = invoiceService.createInvoice(invoice, uploadedByUserId);
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", created));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @PostMapping(value = "/invoices/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadInvoices(
            @RequestParam UUID anchorId,
            @RequestParam UUID programId,
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles,
            @RequestHeader(value = "X-Linked-Entity-Type", required = false) String linkedEntityType,
            @RequestHeader(value = "X-Linked-Entity-Id", required = false) String linkedEntityId) {
        AnchorPortalInvoiceAuth.requireInvoiceUploadRole(roles);
        try {
            UUID resolvedAnchorId = requireAnchorFromHeaders(linkedEntityType, linkedEntityId, anchorId, userId);
            requireProgramBelongsToAnchor(programId, resolvedAnchorId);
            UUID uploadedByUserId = userId != null ? UUID.fromString(userId) : null;
            List<Invoice> results =
                    invoiceService.uploadInvoiceCsv(resolvedAnchorId, programId, file.getInputStream(), uploadedByUserId);
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "data", Map.of("rowsProcessed", results.size(), "records", results)));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @PostMapping(value = "/invoices/{invoiceId}/digital-invoice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadDigitalInvoice(
            @PathVariable UUID invoiceId,
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-User-Roles", required = false) String roles,
            @RequestHeader(value = "X-Linked-Entity-Type", required = false) String linkedEntityType,
            @RequestHeader(value = "X-Linked-Entity-Id", required = false) String linkedEntityId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        AnchorPortalInvoiceAuth.requireInvoiceUploadRole(roles);
        try {
            UUID resolvedAnchorId = requireAnchorFromHeaders(linkedEntityType, linkedEntityId, null, userId);
            Invoice existing = invoiceService.getInvoice(invoiceId);
            requireInvoiceTenant(existing.getAnchorId(), resolvedAnchorId);
            InvoiceDigitalAttachmentResult attachment = invoiceService.attachDigitalInvoice(invoiceId, file);
            Invoice inv = invoiceService.getInvoice(invoiceId);
            Map<String, Object> attachmentPayload = new HashMap<>();
            attachmentPayload.put("storageMode", attachment.storageMode());
            if (attachment.todo() != null) {
                attachmentPayload.put("todo", attachment.todo());
            }
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "data", inv,
                    "attachment", attachmentPayload));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @PostMapping("/invoices/{id}/verify")
    public ResponseEntity<Map<String, Object>> verifyInvoice(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Roles", required = false) String roles,
            @RequestHeader(value = "X-Linked-Entity-Type", required = false) String linkedEntityType,
            @RequestHeader(value = "X-Linked-Entity-Id", required = false) String linkedEntityId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        AnchorPortalInvoiceAuth.requireInvoiceUploadRole(roles);
        UUID resolvedAnchorId = requireAnchorFromHeaders(linkedEntityType, linkedEntityId, null, userId);
        Invoice pending = invoiceService.getInvoice(id);
        requireInvoiceTenant(pending.getAnchorId(), resolvedAnchorId);
        UUID verifiedBy = userId != null ? UUID.fromString(userId) : null;
        Invoice verified = invoiceService.verifyInvoice(id, verifiedBy);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", verified));
    }

    @PostMapping("/invoices/{id}/confirm")
    public ResponseEntity<Map<String, Object>> confirmInvoice(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Roles", required = false) String roles,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Linked-Entity-Type", required = false) String linkedEntityType,
            @RequestHeader(value = "X-Linked-Entity-Id", required = false) String linkedEntityId) {
        AnchorPortalInvoiceAuth.requireInvoiceConfirmRole(roles);
        UUID resolvedAnchorId = requireAnchorFromHeaders(linkedEntityType, linkedEntityId, null, userId);
        Invoice pending = invoiceService.getInvoice(id);
        requireInvoiceTenant(pending.getAnchorId(), resolvedAnchorId);
        AnchorPortalInvoiceAuth.rejectCheckerConfirmingOwnUpload(roles, userId, pending.getUploadedByUserId());
        Invoice confirmed = invoiceService.confirmInvoice(id);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", confirmed));
    }
}
