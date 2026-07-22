package com.plp.program.controller;

import com.plp.program.model.dto.InvoiceCsvUploadResult;
import com.plp.program.model.dto.InvoiceDigitalAttachmentResult;
import com.plp.program.model.entity.Invoice;
import com.plp.program.model.enums.InvoiceDiscountingFlowType;
import com.plp.program.model.entity.Program;
import com.plp.program.repository.ProgramRepository;
import com.plp.program.repository.SubProgramRepository;
import com.plp.program.security.InvoiceAccessGuard;
import com.plp.program.security.InvoiceAccessGuard.InvoiceWriteOperation;
import com.plp.program.security.LenderPortalRoleAuthorization;
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

    @GetMapping
    public ResponseEntity<?> listInvoices(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String lifecycle,
            @RequestParam(required = false) String flowType,
            @RequestParam(required = false) String tab,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader) {
        LenderPortalRoleAuthorization.requireCreditAnalystOrManager(rolesHeader);
        Map<String, Object> paged = invoiceService.listInvoicesPaged(
                search, status, lifecycle, flowType, tab, page != null ? page : 0, size != null ? size : 20);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", paged.get("data"), "page", paged.get("page")));
    }

    @PostMapping
    public ResponseEntity<Invoice> createInvoice(
            @RequestBody Invoice invoice,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        InvoiceAccessGuard.requireManualInvoiceCreateAllowed(invoice, rolesHeader, linkedEntityId, linkedEntityType);
        Set<String> roles = InvoiceAccessGuard.parseRoles(rolesHeader);
        UUID uploadedBy = parseOptionalUserId(userId);
        boolean sellerInitiated = InvoiceDiscountingFlowType.isSellerInitiated(invoice.getFlowType());
        if (InvoiceAccessGuard.isBorrowerRole(roles) || sellerInitiated) {
            return ResponseEntity.ok(invoiceService.createBorrowerInvoice(invoice, uploadedBy));
        }
        return ResponseEntity.ok(invoiceService.createInvoice(invoice, uploadedBy));
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
            InvoiceCsvUploadResult upload =
                    invoiceService.uploadInvoiceCsv(anchorId, programId, file.getInputStream(), null);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "rowsInserted", upload.rowsProcessed(),
                    "rowsSkipped", upload.rowsSkipped(),
                    "errors", upload.errors()));
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

    @PostMapping(value = "/{invoiceId}/digital-invoice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadDigitalInvoice(
            @PathVariable UUID invoiceId,
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false)
                    String linkedEntityType) {
        Invoice invoice = invoiceService.getInvoice(invoiceId);
        InvoiceAccessGuard.requireDigitalInvoiceUploadAccess(invoice, rolesHeader, linkedEntityId, linkedEntityType);
        try {
            InvoiceDigitalAttachmentResult attachment =
                    invoiceService.attachDigitalInvoice(invoiceId, file);
            Invoice inv = invoiceService.getInvoice(invoiceId);
            Map<String, Object> attachmentPayload = Map.of(
                    "storageMode", attachment.storageMode(),
                    "todo", attachment.todo() != null ? attachment.todo() : "");
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "data", inv,
                    "attachment", attachmentPayload));
        } catch (Exception e) {
            log.warn("Digital invoice upload failed for {}: {}", invoiceId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
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
    public ResponseEntity<?> getByBorrower(
            @PathVariable UUID borrowerId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String lifecycle,
            @RequestParam(required = false) String flowType,
            @RequestParam(required = false) String tab,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType) {
        InvoiceAccessGuard.requireBorrowerPathMatchesOrLender(borrowerId, rolesHeader, linkedEntityId, linkedEntityType);
        if (page != null || size != null || flowType != null || tab != null) {
            Map<String, Object> paged = invoiceService.listBorrowerInvoicesPaged(
                    borrowerId,
                    search,
                    status,
                    lifecycle,
                    flowType,
                    tab,
                    page != null ? page : 0,
                    size != null ? size : 20);
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", paged.get("data"), "page", paged.get("page")));
        }
        return ResponseEntity.ok(invoiceService.getByBorrowerEnriched(borrowerId, flowType));
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

    @PostMapping("/{id}/verify")
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

    @PostMapping("/{id}/mark-rejected")
    public ResponseEntity<Invoice> markRejected(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType) {
        Invoice invoice = invoiceService.getInvoice(id);
        InvoiceAccessGuard.requireInvoiceWriteAccess(
                invoice, rolesHeader, linkedEntityId, linkedEntityType, InvoiceWriteOperation.MARK_REJECTED);
        String reason = body != null && body.get("reason") != null ? body.get("reason").toString() : null;
        return ResponseEntity.ok(invoiceService.markRejected(id, reason));
    }

    @PostMapping("/{id}/mark-closed")
    public ResponseEntity<Invoice> markClosed(
            @PathVariable UUID id,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType) {
        Invoice invoice = invoiceService.getInvoice(id);
        InvoiceAccessGuard.requireInvoiceWriteAccess(
                invoice, rolesHeader, linkedEntityId, linkedEntityType, InvoiceWriteOperation.MARK_CLOSED);
        return ResponseEntity.ok(invoiceService.markClosed(id));
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

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteInvoice(
            @PathVariable UUID id,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType) {
        Invoice invoice = invoiceService.getInvoice(id);
        InvoiceAccessGuard.requireInvoiceWriteAccess(
                invoice, rolesHeader, linkedEntityId, linkedEntityType, InvoiceWriteOperation.DELETE);
        try {
            invoiceService.deleteInvoice(id);
            return ResponseEntity.ok(Map.of("status", "SUCCESS"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    /**
     * LOS / borrower-portal delete for seller-initiated invoices (SBD/PO).
     * Uses lender JWT like {@code borrower-accept}, scoped by {@code borrowerId}.
     */
    @DeleteMapping("/{id}/borrower-delete")
    public ResponseEntity<Map<String, Object>> borrowerDeleteInvoice(
            @PathVariable UUID id,
            @RequestParam UUID borrowerId,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_LINKED_ENTITY_ID, required = false) String linkedEntityId,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_LINKED_ENTITY_TYPE, required = false) String linkedEntityType) {
        Invoice invoice = invoiceService.getInvoice(id);
        UUID effectiveBorrowerId = InvoiceAccessGuard.resolveBorrowerIdForAccept(
                invoice, rolesHeader, linkedEntityId, linkedEntityType, borrowerId);
        InvoiceAccessGuard.requireBorrowerOwnedSellerInvoiceForDelete(invoice, effectiveBorrowerId);
        try {
            invoiceService.deleteInvoice(id);
            return ResponseEntity.ok(Map.of("status", "SUCCESS"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @PostMapping("/{id}/pip-adjust")
    public ResponseEntity<Invoice> adjustPip(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = InvoiceAccessGuard.HEADER_USER_ROLES, required = false) String rolesHeader) {
        if (!LenderPortalRoleAuthorization.parseRoles(rolesHeader).contains("PLATFORM_ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "PIP adjust is restricted to internal system calls");
        }
        BigDecimal principalAdd = decimal(body.get("principalAdd"));
        BigDecimal principalSubtract = decimal(body.get("principalSubtract"));
        BigDecimal discountAdd = decimal(body.get("discountAdd"));
        BigDecimal discountSubtract = decimal(body.get("discountSubtract"));
        return ResponseEntity.ok(invoiceService.adjustPipAmounts(
                id, principalAdd, principalSubtract, discountAdd, discountSubtract));
    }

    private static BigDecimal decimal(Object raw) {
        if (raw == null) {
            return null;
        }
        return new BigDecimal(raw.toString());
    }

    private static UUID parseOptionalUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(userId.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
