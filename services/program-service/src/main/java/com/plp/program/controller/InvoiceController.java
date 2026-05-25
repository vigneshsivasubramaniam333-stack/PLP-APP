package com.plp.program.controller;

import com.plp.program.model.entity.Invoice;
import com.plp.program.model.entity.Program;
import com.plp.program.repository.ProgramRepository;
import com.plp.program.repository.SubProgramRepository;
import com.plp.program.security.InvoiceAccessGuard;
import com.plp.program.security.InvoiceAccessGuard.InvoiceWriteOperation;
import com.plp.program.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
@Slf4j
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final ProgramRepository programRepository;
    private final SubProgramRepository subProgramRepository;

    @PostMapping
    public ResponseEntity<Invoice> createInvoice(
            @RequestBody Invoice invoice,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType) {
        InvoiceAccessGuard.requireManualInvoiceCreateAllowed(invoice, rolesHeader, linkedEntityId, linkedEntityType);
        return ResponseEntity.ok(invoiceService.createInvoice(invoice));
    }

    @PostMapping("/upload-csv")
    public ResponseEntity<Map<String, Object>> uploadCsv(
            @RequestParam UUID anchorId,
            @RequestParam UUID programId,
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType) {
        InvoiceAccessGuard.requireCsvUploadAllowed(anchorId, rolesHeader, linkedEntityId, linkedEntityType);
        try {
            List<Invoice> invoices =
                    invoiceService.uploadInvoiceCsv(anchorId, programId, file.getInputStream(), null);
            return ResponseEntity.ok(Map.of("status", "success", "rowsInserted", invoices.size()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Invoice> getInvoice(
            @PathVariable UUID id,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType) {
        Invoice invoice = invoiceService.getInvoice(id);
        InvoiceAccessGuard.requireInvoiceReadAccess(invoice, rolesHeader, linkedEntityId, linkedEntityType);
        return ResponseEntity.ok(invoice);
    }

    @GetMapping("/{invoiceId}/digital-invoice/download")
    public ResponseEntity<byte[]> downloadDigitalInvoice(
            @PathVariable UUID invoiceId,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false)
                    String linkedEntityType) {
        Invoice invoice = invoiceService.getInvoice(invoiceId);
        InvoiceAccessGuard.requireInvoiceReadAccess(invoice, rolesHeader, linkedEntityId, linkedEntityType);
        InvoiceService.DigitalInvoiceDownload d = invoiceService.loadDigitalInvoiceDownload(invoice);

        HttpHeaders headers = new HttpHeaders();
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        try {
            String ct = d.contentType();
            if (ct != null && !ct.isBlank()) {
                mediaType = MediaType.parseMediaType(ct);
            }
        } catch (Exception ignored) {
        }
        headers.setContentType(mediaType);

        boolean inline =
                MediaType.APPLICATION_PDF.equals(mediaType) || "image".equalsIgnoreCase(mediaType.getType());
        String safeName = sanitizeDigitalInvoiceDownloadFilename(d.fileName());
        ContentDisposition disposition =
                (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                        .filename(safeName, StandardCharsets.UTF_8)
                        .build();
        headers.setContentDisposition(disposition);

        return ResponseEntity.ok().headers(headers).body(d.body());
    }

    private static String sanitizeDigitalInvoiceDownloadFilename(String name) {
        if (name == null || name.isBlank()) {
            return "invoice.bin";
        }
        return name.replace("\r", "_").replace("\n", "_").replace("\"", "_");
    }

    @GetMapping("/anchor/{anchorId}")
    public ResponseEntity<List<Invoice>> getByAnchor(
            @PathVariable UUID anchorId,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType) {
        InvoiceAccessGuard.requireAnchorPathMatchesOrLender(anchorId, rolesHeader, linkedEntityId, linkedEntityType);
        return ResponseEntity.ok(invoiceService.getByAnchor(anchorId));
    }

    @GetMapping("/borrower/{borrowerId}")
    public ResponseEntity<List<Invoice>> getByBorrower(
            @PathVariable UUID borrowerId,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType) {
        InvoiceAccessGuard.requireBorrowerPathMatchesOrLender(borrowerId, rolesHeader, linkedEntityId, linkedEntityType);
        return ResponseEntity.ok(invoiceService.getByBorrower(borrowerId));
    }

    @GetMapping("/borrower/{borrowerId}/eligible")
    public ResponseEntity<List<Invoice>> getEligibleByBorrower(
            @PathVariable UUID borrowerId,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType) {
        InvoiceAccessGuard.requireBorrowerPathMatchesOrLender(borrowerId, rolesHeader, linkedEntityId, linkedEntityType);
        return ResponseEntity.ok(invoiceService.getEligibleByBorrower(borrowerId));
    }

    @GetMapping("/program/{programId}")
    public ResponseEntity<List<Invoice>> getByProgram(
            @PathVariable UUID programId,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType) {
        Program program = programRepository
                .findById(programId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Program not found: " + programId));
        boolean anchorMayAccess = false;
        Set<String> roles = InvoiceAccessGuard.parseRoles(rolesHeader);
        if (InvoiceAccessGuard.isAnchorRole(roles)) {
            UUID linkedAnchor = parseInvoiceLinkedAnchorId(linkedEntityId, linkedEntityType);
            if (linkedAnchor != null) {
                anchorMayAccess =
                        program.getAnchorId() != null
                                ? program.getAnchorId().equals(linkedAnchor)
                                : subProgramRepository.existsByProgramIdAndAnchorId(program.getId(), linkedAnchor);
            }
        }
        InvoiceAccessGuard.requireProgramInvoiceListAccess(
                program, anchorMayAccess, rolesHeader, linkedEntityId, linkedEntityType);
        return ResponseEntity.ok(invoiceService.getByProgram(programId));
    }

    private static UUID parseInvoiceLinkedAnchorId(String linkedEntityId, String linkedEntityType) {
        String t = linkedEntityType == null ? "" : linkedEntityType.trim();
        if (!"ANCHOR".equalsIgnoreCase(t) || linkedEntityId == null || linkedEntityId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(linkedEntityId.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    public ResponseEntity<Invoice> verifyInvoice(
            @PathVariable UUID id,
            @RequestParam UUID verifiedBy,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType) {
        Invoice invoice = invoiceService.getInvoice(id);
        InvoiceAccessGuard.requireInvoiceWriteAccess(
                invoice, rolesHeader, linkedEntityId, linkedEntityType, InvoiceWriteOperation.VERIFY);
        return ResponseEntity.ok(invoiceService.verifyInvoice(id, verifiedBy));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<Invoice> confirmInvoice(
            @PathVariable UUID id,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType) {
        Invoice invoice = invoiceService.getInvoice(id);
        InvoiceAccessGuard.requireInvoiceWriteAccess(
                invoice, rolesHeader, linkedEntityId, linkedEntityType, InvoiceWriteOperation.CONFIRM);
        return ResponseEntity.ok(invoiceService.confirmInvoice(id));
    }

    @PostMapping("/{id}/borrower-accept")
    public ResponseEntity<Invoice> borrowerAccept(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID borrowerId,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType) {
        Invoice invoice = invoiceService.getInvoice(id);
        UUID effectiveBorrowerId =
                InvoiceAccessGuard.resolveBorrowerIdForAccept(
                        invoice, rolesHeader, linkedEntityId, linkedEntityType, borrowerId);
        InvoiceAccessGuard.requireInvoiceBorrowerAccess(invoice, rolesHeader, linkedEntityId, linkedEntityType);
        return ResponseEntity.ok(invoiceService.borrowerAcceptInvoice(id, effectiveBorrowerId));
    }

    @PostMapping("/{id}/mark-financing-requested")
    public ResponseEntity<Invoice> markFinancingRequested(
            @PathVariable UUID id,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType) {
        Invoice invoice = invoiceService.getInvoice(id);
        InvoiceAccessGuard.requireInvoiceWriteAccess(
                invoice, rolesHeader, linkedEntityId, linkedEntityType, InvoiceWriteOperation.MARK_FINANCING_REQUESTED);
        log.info("Received mark-financing-requested for {}", id);
        return ResponseEntity.ok(invoiceService.markFinancingRequested(id));
    }

    @PostMapping("/{id}/cancel-financing-requested")
    public ResponseEntity<Invoice> cancelFinancingRequested(
            @PathVariable UUID id,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType) {
        Invoice invoice = invoiceService.getInvoice(id);
        InvoiceAccessGuard.requireInvoiceWriteAccess(
                invoice, rolesHeader, linkedEntityId, linkedEntityType, InvoiceWriteOperation.CANCEL_FINANCING_REQUESTED);
        log.info("Received cancel-financing-requested for {}", id);
        return ResponseEntity.ok(invoiceService.revertFinancingRequestedForLoanDisburseCancel(id));
    }

    @PostMapping("/{id}/mark-discounted")
    public ResponseEntity<Invoice> markDiscounted(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType) {
        Invoice invoice = invoiceService.getInvoice(id);
        InvoiceAccessGuard.requireInvoiceWriteAccess(
                invoice, rolesHeader, linkedEntityId, linkedEntityType, InvoiceWriteOperation.MARK_DISCOUNTED);
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        return ResponseEntity.ok(invoiceService.markDiscounted(id, amount));
    }
}
