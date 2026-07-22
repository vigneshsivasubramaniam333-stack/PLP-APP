package com.plp.program.service;

import com.plp.program.audit.EntityAuditHelper;
import com.plp.program.model.dto.InvoiceCsvUploadResult;
import com.plp.program.model.dto.InvoiceDigitalAttachmentResult;
import com.plp.program.model.entity.Borrower;
import com.plp.program.model.entity.Invoice;
import com.plp.program.model.entity.Program;
import com.plp.program.model.entity.SubProgram;
import com.plp.program.model.enums.InvoiceDiscountingFlowType;
import com.plp.program.model.enums.InvoiceStatus;
import com.plp.program.service.earlypay.EarlyPayEligibilityService;
import com.plp.program.validation.ProgramParametersValidator;
import com.plp.program.repository.BorrowerRepository;
import com.plp.program.repository.InvoiceRepository;
import com.plp.program.repository.ProgramRepository;
import com.plp.program.repository.SubProgramBorrowerRepository;
import com.plp.program.repository.SubProgramRepository;
import com.plp.program.storage.DigitalInvoiceObjectStorage;
import com.plp.program.storage.LocalDigitalInvoiceStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final BorrowerRepository borrowerRepository;
    private final ProgramRepository programRepository;
    private final SubProgramRepository subProgramRepository;
    private final SubProgramBorrowerRepository subProgramBorrowerRepository;
    private final DigitalInvoiceObjectStorage digitalInvoiceObjectStorage;
    private final LocalDigitalInvoiceStorage localDigitalInvoiceStorage;
    private final ProgramService programService;
    private final InvoiceAutoFinanceService invoiceAutoFinanceService;
    private final EarlyPayEligibilityService earlyPayEligibilityService;
    private final EntityAuditHelper entityAuditHelper;

    private static final List<String> GAP_BLOCKING_STATUSES = List.of(
            InvoiceStatus.FINANCING_REQUESTED.name(),
            "PARTIALLY_DISCOUNTED",
            "FULLY_DISCOUNTED");
    private static final List<String> DELETABLE_STATUSES = List.of(
            "UPLOADED",
            "VERIFIED",
            "ELIGIBLE",
            "BORROWER_ACCEPTED",
            "REJECTED"
    );

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final List<DateTimeFormatter> CSV_DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("d-M-yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH));

    private static final String FLOW_PURCHASE_BILL_DISCOUNTING = InvoiceDiscountingFlowType.PURCHASE_BILL_DISCOUNTING;
    private static final String FLOW_SALES_BILL_DISCOUNTING = InvoiceDiscountingFlowType.SALES_BILL_DISCOUNTING;

    @Transactional
    public Invoice createInvoice(Invoice invoice) {
        return createInvoice(invoice, null, true);
    }

    @Transactional
    public Invoice createInvoice(Invoice invoice, UUID uploadedByUserId) {
        return createInvoice(invoice, uploadedByUserId, true);
    }

    @Transactional
    public Invoice createBorrowerInvoice(Invoice invoice, UUID uploadedByUserId) {
        return createInvoice(invoice, uploadedByUserId, false);
    }

    private Invoice createInvoice(Invoice invoice, UUID uploadedByUserId, boolean anchorInitiated) {
        validateInvoice(invoice);
        applySubProgramLinkForCreate(invoice);
        if (invoice.getSubProgramId() == null) {
            applyFlowTypeDefaultOrValidate(invoice);
        }
        String flow = invoice.getFlowType();
        if (anchorInitiated) {
            if (InvoiceDiscountingFlowType.isSellerInitiated(flow)) {
                throw new RuntimeException("Anchor cannot create invoices for seller-initiated flow types");
            }
        } else {
            if (!InvoiceDiscountingFlowType.isSellerInitiated(flow)) {
                throw new RuntimeException(
                        "Borrower invoice create is only allowed for SALES_BILL_DISCOUNTING or "
                                + "PURCHASE_ORDER_DISCOUNTING");
            }
        }
        computeEligibleAmount(invoice);
        enforceGapBetweenPreviousInvoices(invoice);
        boolean createAsApproved = anchorInitiated && Boolean.TRUE.equals(invoice.getCreateAsApproved());
        if (createAsApproved) {
            requirePurchaseBillFlow(invoice, "create as approved");
            invoice.setVerified(true);
            invoice.setVerifiedAt(Instant.now());
            invoice.setVerifiedBy(uploadedByUserId);
            invoice.setAnchorConfirmed(true);
            invoice.setAnchorConfirmedAt(Instant.now());
            invoice.setStatus("ELIGIBLE");
        } else {
            invoice.setStatus("UPLOADED");
        }
        invoice.setSource("MANUAL");
        invoice.setUploadedByUserId(uploadedByUserId);
        invoice = invoiceRepository.save(invoice);
        entityAuditHelper.captureCreate(
                "INVOICE",
                invoice.getId().toString(),
                invoice,
                uploadedByUserId != null ? uploadedByUserId.toString() : null,
                null,
                null,
                null,
                "Invoice created with status " + invoice.getStatus());
        if (createAsApproved) {
            invoice = applyPostEligibleAutomation(invoice);
        }
        log.info("Invoice created: {} anchor={} borrower={} subProgram={} amount={} status={}",
                invoice.getInvoiceNumber(), invoice.getAnchorId(), invoice.getBorrowerId(),
                invoice.getSubProgramId(), invoice.getInvoiceAmount(), invoice.getStatus());
        return invoice;
    }

    @Transactional
    public InvoiceCsvUploadResult uploadInvoiceCsv(
            UUID anchorId, UUID programId, InputStream csvStream, UUID uploadedByUserId) {
        return uploadInvoiceCsv(anchorId, programId, csvStream, uploadedByUserId, null);
    }

    @Transactional
    public InvoiceCsvUploadResult uploadInvoiceCsv(
            UUID anchorId,
            UUID programId,
            InputStream csvStream,
            UUID uploadedByUserId,
            UUID defaultSubProgramId) {
        Program program = programRepository.findById(programId)
                .orElseThrow(() -> new RuntimeException("Program not found: " + programId));

        BigDecimal marginPct = program.getMarginPercent() != null ? program.getMarginPercent() : BigDecimal.ZERO;

        List<Invoice> results = new ArrayList<>();
        List<InvoiceCsvUploadResult.CsvRowError> errors = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(csvStream))) {
            String header = reader.readLine();
            if (header == null) {
                throw new RuntimeException("CSV file is empty");
            }
            if (header.startsWith("\uFEFF")) {
                header = header.substring(1);
            }

            Map<String, Integer> headerIndex = parseInvoiceCsvHeader(header);

            String line;
            int rowNum = 1;
            while ((line = reader.readLine()) != null) {
                rowNum++;
                if (line.isBlank()) {
                    continue;
                }
                String[] cols = line.split(",", -1);
                if (cols.length < 6) {
                    skipCsvRow(
                            rowNum,
                            "insufficient columns (need invoiceNumber, borrowerCode or partyCode, invoiceDate, dueDate, invoiceAmount, taxAmount)",
                            errors);
                    continue;
                }

                CsvInvoiceRow parsed = extractInvoiceCsvRow(cols, headerIndex, rowNum);
                if (parsed == null) {
                    skipCsvRow(rowNum, "could not parse row (check headers and column count)", errors);
                    continue;
                }

                String invoiceNumber = parsed.invoiceNumber();
                String borrowerCode = parsed.borrowerCode();
                String partyCode = parsed.partyCode();
                String invoiceDateStr = parsed.invoiceDateStr();
                String dueDateStr = parsed.dueDateStr();
                String invoiceAmtStr = parsed.invoiceAmtStr();
                String taxAmtStr = parsed.taxAmtStr();
                String flowRaw = parsed.flowRaw();
                String subProgramCode = parsed.subProgramCode();
                String subProgramIdRaw = parsed.subProgramIdRaw();

                if (invoiceNumber.isEmpty()) {
                    skipCsvRow(rowNum, "missing invoiceNumber", errors);
                    continue;
                }
                if (borrowerCode.isEmpty() && partyCode.isEmpty()) {
                    skipCsvRow(rowNum, "missing borrowerCode or partyCode", errors);
                    continue;
                }

                CsvSubProgramPick subPick = pickCsvSubProgram(subProgramCode, subProgramIdRaw, rowNum);
                if (subPick.skipRow()) {
                    skipCsvRow(rowNum, "invalid sub-program reference", errors);
                    continue;
                }
                UUID csvSubProgramId = subPick.subProgramId();
                if (csvSubProgramId == null && defaultSubProgramId != null) {
                    csvSubProgramId = defaultSubProgramId;
                }

                Optional<Borrower> borrowerOpt =
                        resolveCsvBorrower(borrowerCode, partyCode, csvSubProgramId, anchorId);
                if (borrowerOpt.isEmpty()) {
                    skipCsvRow(
                            rowNum,
                            partyCode.isEmpty()
                                    ? "borrower not found: " + borrowerCode
                                    : "party code not found: " + partyCode,
                            errors);
                    continue;
                }

                LocalDate invoiceDate = parseCsvLocalDate(invoiceDateStr);
                LocalDate dueDate = parseCsvLocalDate(dueDateStr);
                if (invoiceDate != null && dueDate != null && dueDate.isBefore(invoiceDate)) {
                    skipCsvRow(rowNum, "due date cannot be before invoice date", errors);
                    continue;
                }
                if (invoiceDate == null || dueDate == null) {
                    skipCsvRow(
                            rowNum,
                            "invalid date format (use yyyy-MM-dd, e.g. 2026-06-01). invoiceDate='"
                                    + invoiceDateStr
                                    + "', dueDate='"
                                    + dueDateStr
                                    + "'",
                            errors);
                    continue;
                }

                if (findDuplicateInvoice(borrowerOpt.get().getId(), invoiceNumber, invoiceDate, dueDate).isPresent()) {
                    skipCsvRow(
                            rowNum,
                            "duplicate invoice for this borrower (same number, invoice date, and due date)",
                            errors);
                    continue;
                }

                BigDecimal invoiceAmt;
                BigDecimal taxAmt;
                try {
                    invoiceAmt = new BigDecimal(invoiceAmtStr.replace(",", ""));
                    taxAmt = taxAmtStr.isEmpty() ? BigDecimal.ZERO : new BigDecimal(taxAmtStr.replace(",", ""));
                } catch (NumberFormatException e) {
                    skipCsvRow(rowNum, "invalid amount", errors);
                    continue;
                }

                if (invoiceAmt.compareTo(BigDecimal.ZERO) <= 0) {
                    skipCsvRow(rowNum, "invoice amount must be positive", errors);
                    continue;
                }

                BigDecimal netAmount = invoiceAmt.add(taxAmt);

                SubProgram linkedSub = null;
                if (csvSubProgramId != null) {
                    linkedSub = subProgramRepository.findById(csvSubProgramId).orElse(null);
                    if (linkedSub == null) {
                        skipCsvRow(rowNum, "sub-program not found: " + csvSubProgramId, errors);
                        continue;
                    }
                    try {
                        validateInvoiceAgainstSubProgram(linkedSub, programId, anchorId, borrowerOpt.get().getId());
                    } catch (RuntimeException e) {
                        skipCsvRow(rowNum, e.getMessage() != null ? e.getMessage() : "sub-program validation failed", errors);
                        continue;
                    }
                }

                final String resolvedFlowType;
                final UUID invoiceSubProgramId;
                if (linkedSub != null) {
                    if (InvoiceDiscountingFlowType.isSellerInitiated(linkedSub.getFlowType())) {
                        skipCsvRow(rowNum, "anchor CSV upload supports PURCHASE_BILL_DISCOUNTING only", errors);
                        continue;
                    }
                    resolvedFlowType = linkedSub.getFlowType();
                    invoiceSubProgramId = linkedSub.getId();
                } else {
                    String rft = resolveFlowTypeForCsv(flowRaw, rowNum);
                    if (rft == null) {
                        skipCsvRow(rowNum, "invalid flowType (use PURCHASE_BILL_DISCOUNTING only for anchor CSV)", errors);
                        continue;
                    }
                    resolvedFlowType = rft;
                    invoiceSubProgramId = null;
                }

                Invoice invoice = Invoice.builder()
                        .invoiceNumber(invoiceNumber)
                        .anchorId(anchorId)
                        .borrowerId(borrowerOpt.get().getId())
                        .programId(programId)
                        .subProgramId(invoiceSubProgramId)
                        .flowType(resolvedFlowType)
                        .invoiceDate(invoiceDate)
                        .dueDate(dueDate)
                        .invoiceAmount(invoiceAmt)
                        .taxAmount(taxAmt)
                        .netAmount(netAmount)
                        .marginPercent(marginPct)
                        .source("MANUAL")
                        .status("UPLOADED")
                        .uploadedByUserId(uploadedByUserId)
                        .build();

                computeEligibleAmount(invoice);
                invoice = invoiceRepository.save(invoice);
                results.add(invoice);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Error processing invoice CSV: " + e.getMessage(), e);
        }

        log.info("Invoice CSV upload: anchor={} program={} rows={} skipped={}", anchorId, programId, results.size(), errors.size());
        return new InvoiceCsvUploadResult(results, results.size(), errors.size(), errors);
    }

    private void skipCsvRow(int rowNum, String reason, List<InvoiceCsvUploadResult.CsvRowError> errors) {
        log.warn("Skipping row {}: {}", rowNum, reason);
        errors.add(new InvoiceCsvUploadResult.CsvRowError(rowNum, reason));
    }

    @Transactional
    public Invoice verifyInvoice(UUID invoiceId, UUID verifiedBy) {
        Invoice invoice = getInvoice(invoiceId);
        requirePurchaseBillFlow(invoice, "verify");
        String previousStatus = invoice.getStatus();
        invoice.setVerified(true);
        invoice.setVerifiedAt(Instant.now());
        invoice.setVerifiedBy(verifiedBy);
        if ("UPLOADED".equals(invoice.getStatus())) {
            invoice.setStatus("VERIFIED");
        }
        invoice = invoiceRepository.save(invoice);
        recordStatusChange(invoice, previousStatus, "Invoice verified");
        return invoice;
    }

    @Transactional
    public Invoice confirmInvoice(UUID invoiceId) {
        Invoice invoice = getInvoice(invoiceId);
        requirePurchaseBillFlow(invoice, "confirm");
        if (!"VERIFIED".equals(invoice.getStatus())) {
            throw new RuntimeException(
                    "Invoice must be in VERIFIED state before anchor confirmation. Current status: " + invoice.getStatus());
        }
        String previousStatus = invoice.getStatus();
        invoice.setAnchorConfirmed(true);
        invoice.setAnchorConfirmedAt(Instant.now());
        invoice.setStatus("ELIGIBLE");
        invoice = invoiceRepository.save(invoice);
        recordStatusChange(invoice, previousStatus, "Invoice confirmed by anchor");
        return applyPostEligibleAutomation(invoice);
    }

    private Invoice applyPostEligibleAutomation(Invoice invoice) {
        if (InvoiceDiscountingFlowType.isSellerInitiated(invoice.getFlowType())) {
            return invoice;
        }
        Map<String, Object> params = programService.getProgramParameters(invoice.getProgramId());
        if (ProgramParametersValidator.parseYesNo(params.get("autoAcceptInvoices"), false)) {
            String flow = invoice.getFlowType();
            boolean purchaseFlow = InvoiceDiscountingFlowType.isPurchaseBill(flow);
            if (purchaseFlow && "ELIGIBLE".equals(invoice.getStatus())) {
                invoice = borrowerAcceptInvoice(invoice.getId(), invoice.getBorrowerId());
            }
        } else if (ProgramParametersValidator.parseYesNo(params.get("autoPullOption"), false)) {
            maybeAutoPullFinance(invoice);
        }
        return invoice;
    }

    private void maybeAutoPullFinance(Invoice invoice) {
        invoiceAutoFinanceService.maybeAutoPullFinance(invoice);
    }

    /**
     * Purchase bill discounting (and legacy null flow): borrower acknowledges invoice after anchor marked it ELIGIBLE.
     */
    @Transactional
    public Invoice borrowerAcceptInvoice(UUID invoiceId, UUID borrowerId) {
        Invoice invoice = getInvoice(invoiceId);
        if (!invoice.getBorrowerId().equals(borrowerId)) {
            throw new RuntimeException("Invoice does not belong to this borrower");
        }
        String flow = invoice.getFlowType();
        if (!InvoiceDiscountingFlowType.isPurchaseBill(flow)) {
            throw new RuntimeException("Borrower acceptance does not apply to seller-initiated flow invoices");
        }
        if (!"ELIGIBLE".equals(invoice.getStatus()) && !InvoiceStatus.REJECTED.name().equals(invoice.getStatus())) {
            throw new RuntimeException(
                    "Borrower acceptance allowed only when invoice status is ELIGIBLE or REJECTED. Current status: "
                            + invoice.getStatus());
        }
        invoice.setBorrowerAccepted(true);
        invoice.setBorrowerAcceptedAt(Instant.now());
        String previousStatus = invoice.getStatus();
        invoice.setStatus("BORROWER_ACCEPTED");
        invoice = invoiceRepository.save(invoice);
        recordStatusChange(invoice, previousStatus, "Invoice accepted by borrower");
        maybeAutoPullFinance(invoice);
        return invoiceRepository.findById(invoiceId).orElse(invoice);
    }

    /**
     * Seller-initiated (SBD/PO): anchor approves borrower-uploaded invoice pending review.
     */
    @Transactional
    public Invoice anchorApproveSellerInvoice(UUID invoiceId, UUID anchorId) {
        Invoice invoice = getInvoice(invoiceId);
        if (!invoice.getAnchorId().equals(anchorId)) {
            throw new RuntimeException("Invoice does not belong to this anchor");
        }
        requireSellerInitiatedFlow(invoice, "approve");
        if (!"UPLOADED".equals(invoice.getStatus())) {
            throw new RuntimeException(
                    "Anchor approval allowed only when invoice status is UPLOADED. Current: " + invoice.getStatus());
        }
        invoice.setVerified(true);
        invoice.setVerifiedAt(Instant.now());
        invoice.setAnchorConfirmed(true);
        invoice.setAnchorConfirmedAt(Instant.now());
        String previousStatus = invoice.getStatus();
        invoice.setStatus("ELIGIBLE");
        invoice = invoiceRepository.save(invoice);
        recordStatusChange(invoice, previousStatus, "Seller invoice approved by anchor");
        return invoice;
    }

    /**
     * Seller-initiated (SBD/PO): anchor rejects borrower-uploaded invoice.
     */
    @Transactional
    public Invoice anchorRejectSellerInvoice(UUID invoiceId, UUID anchorId, String reason) {
        Invoice invoice = getInvoice(invoiceId);
        if (!invoice.getAnchorId().equals(anchorId)) {
            throw new RuntimeException("Invoice does not belong to this anchor");
        }
        requireSellerInitiatedFlow(invoice, "reject");
        if (!"UPLOADED".equals(invoice.getStatus())) {
            throw new RuntimeException(
                    "Anchor rejection allowed only when invoice status is UPLOADED. Current: " + invoice.getStatus());
        }
        String previousStatus = invoice.getStatus();
        invoice.setStatus(InvoiceStatus.REJECTED.name());
        invoice.setRejectionReason(reason);
        invoice.setRejectedAt(Instant.now());
        invoice = invoiceRepository.save(invoice);
        recordStatusChange(invoice, previousStatus, "Seller invoice rejected by anchor");
        return invoice;
    }

    /**
     * Lending-service calls this before persisting an invoice-discounting loan row so invoice and loan states stay aligned.
     */
    @Transactional
    public Invoice markFinancingRequested(UUID invoiceId) {
        Invoice invoice = invoiceRepository.findByIdForUpdate(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found: " + invoiceId));
        String current = invoice.getStatus();
        if (InvoiceStatus.FINANCING_REQUESTED.name().equals(current)) {
            throw new RuntimeException("Financing already requested for this invoice");
        }
        if (InvoiceStatus.DISCOUNTED_EP.name().equals(current)
                || InvoiceStatus.SANCTIONED_EP.name().equals(current)) {
            throw new RuntimeException("Invoice is on Early Pay path; lender finance is not allowed");
        }
        String flow = invoice.getFlowType();
        boolean ok;
        if (InvoiceDiscountingFlowType.isPurchaseBill(flow)) {
            ok = "BORROWER_ACCEPTED".equals(current) || "PARTIALLY_DISCOUNTED".equals(current);
        } else if (InvoiceDiscountingFlowType.isSellerInitiated(flow)) {
            ok = "ELIGIBLE".equals(current) || "PARTIALLY_DISCOUNTED".equals(current);
        } else {
            ok = false;
        }
        if (!ok) {
            throw new RuntimeException("Invoice cannot transition to FINANCING_REQUESTED from status: " + current);
        }
        log.info("Updating invoice {} status from {} to FINANCING_REQUESTED", invoiceId, current);
        invoice.setStatus(InvoiceStatus.FINANCING_REQUESTED.name());
        invoice.setLastFinanceRequestedAt(Instant.now());
        invoice = invoiceRepository.save(invoice);
        recordStatusChange(invoice, current, "Financing requested");
        return invoice;
    }

    /**
     * Lending-service: loan rejected after finance was requested — invoice shows as REJECTED on portals.
     */
    @Transactional
    public Invoice markRejected(UUID invoiceId, String reason) {
        Invoice invoice = invoiceRepository.findByIdForUpdate(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found: " + invoiceId));
        String current = invoice.getStatus();
        if (!InvoiceStatus.FINANCING_REQUESTED.name().equals(current)) {
            throw new RuntimeException("Invoice cannot be marked REJECTED from status: " + current);
        }
        invoice.setStatus(InvoiceStatus.REJECTED.name());
        invoice.setRejectionReason(reason);
        invoice.setRejectedAt(Instant.now());
        invoice = invoiceRepository.save(invoice);
        recordStatusChange(invoice, current, "Invoice rejected after finance request");
        log.info("Invoice {} marked REJECTED: {}", invoiceId, reason);
        return invoice;
    }

    /**
     * Lending-service: all loans on invoice repaid — invoice lifecycle closed.
     */
    @Transactional
    public Invoice markClosed(UUID invoiceId) {
        Invoice invoice = invoiceRepository.findByIdForUpdate(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found: " + invoiceId));
        String current = invoice.getStatus();
        if (InvoiceStatus.CLOSED.name().equals(current)) {
            return invoice;
        }
        if (!InvoiceStatus.FULLY_DISCOUNTED.name().equals(current)
                && !InvoiceStatus.PARTIALLY_DISCOUNTED.name().equals(current)) {
            throw new RuntimeException("Invoice cannot be marked CLOSED from status: " + current);
        }
        invoice.setStatus(InvoiceStatus.CLOSED.name());
        invoice.setClosedAt(Instant.now());
        invoice = invoiceRepository.save(invoice);
        recordStatusChange(invoice, current, "Invoice closed after repayment");
        log.info("Invoice {} marked CLOSED", invoiceId);
        return invoice;
    }

    /**
     * Lending-service: undo FINANCING_REQUESTED when disbursement workflow is cancelled before disbursement completes.
     * Restores a financeable status symmetric to {@link #markFinancingRequested(UUID)}.
     */
    @Transactional
    public Invoice revertFinancingRequestedForLoanDisburseCancel(UUID invoiceId) {
        Invoice invoice = invoiceRepository.findByIdForUpdate(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found: " + invoiceId));
        String current = invoice.getStatus();
        if (!InvoiceStatus.FINANCING_REQUESTED.name().equals(current)) {
            return invoice;
        }
        String flow = invoice.getFlowType();
        boolean purchaseFlow = flow == null || flow.isBlank() || FLOW_PURCHASE_BILL_DISCOUNTING.equals(flow);
        boolean sellerInitiated = !purchaseFlow;
        BigDecimal disc = invoice.getDiscountedAmount() != null ? invoice.getDiscountedAmount() : BigDecimal.ZERO;
        boolean partial = disc.compareTo(BigDecimal.ZERO) > 0;
        if (purchaseFlow) {
            invoice.setStatus(partial ? "PARTIALLY_DISCOUNTED" : "BORROWER_ACCEPTED");
        } else if (sellerInitiated) {
            invoice.setStatus(partial ? "PARTIALLY_DISCOUNTED" : "ELIGIBLE");
        } else {
            throw new RuntimeException("Cannot revert FINANCING_REQUESTED for flowType: " + flow);
        }
        invoice = invoiceRepository.save(invoice);
        recordStatusChange(invoice, current, "Financing request reverted");
        log.info("Reverted invoice {} from FINANCING_REQUESTED to {}", invoiceId, invoice.getStatus());
        return invoice;
    }

    @Transactional
    public Invoice markDiscounted(UUID invoiceId, BigDecimal discountedAmount) {
        Invoice invoice = invoiceRepository.findByIdForUpdate(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found: " + invoiceId));
        Map<String, Object> params = programService.getProgramParameters(invoice.getProgramId());
        boolean partialAllowed = ProgramParametersValidator.parseYesNo(params.get("partialDiscount"), false);
        BigDecimal existing = invoice.getDiscountedAmount() != null ? invoice.getDiscountedAmount() : BigDecimal.ZERO;
        if (!partialAllowed && existing.compareTo(BigDecimal.ZERO) > 0) {
            throw new RuntimeException("Partial discount is not allowed for this program");
        }
        BigDecimal newDiscounted = existing.add(discountedAmount);
        String previousStatus = invoice.getStatus();
        invoice.setDiscountedAmount(newDiscounted);
        invoice.setAvailableAmount(invoice.getEligibleAmount().subtract(newDiscounted));
        if (invoice.getAvailableAmount().compareTo(BigDecimal.ZERO) <= 0) {
            invoice.setAvailableAmount(BigDecimal.ZERO);
            invoice.setStatus("FULLY_DISCOUNTED");
        } else {
            invoice.setStatus("PARTIALLY_DISCOUNTED");
        }
        invoice = invoiceRepository.save(invoice);
        recordStatusChange(invoice, previousStatus, "Invoice discount recorded");
        return invoice;
    }

    @Transactional
    public void deleteInvoice(UUID invoiceId) {
        Invoice invoice = invoiceRepository.findByIdForUpdate(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found: " + invoiceId));
        Map<String, Object> params = programService.getProgramParameters(invoice.getProgramId());
        if (!ProgramParametersValidator.parseYesNo(params.get("invoiceDelete"), false)) {
            throw new RuntimeException("Invoice delete is not enabled for this program");
        }
        String status = invoice.getStatus();
        if (status != null && "FINANCING_REQUESTED".equalsIgnoreCase(status.trim())) {
            throw new RuntimeException("Invoice cannot be deleted after finance has been requested");
        }
        if (status == null || !DELETABLE_STATUSES.contains(status)) {
            throw new RuntimeException("Invoice cannot be deleted in status: " + status);
        }
        entityAuditHelper.captureDelete(
                "INVOICE",
                invoiceId.toString(),
                invoice,
                null,
                null,
                null,
                null,
                "Invoice deleted before finance request");
        invoiceRepository.delete(invoice);
        log.info("Invoice deleted: {} status={}", invoiceId, status);
    }

    private void enforceGapBetweenPreviousInvoices(Invoice invoice) {
        if (invoice.getSubProgramId() == null) {
            return;
        }
        Map<String, Object> params = programService.getProgramParameters(invoice.getProgramId());
        int gapDays = parseIntParam(params.get("gapBetweenPreviousInvoiceDays"), 0);
        if (gapDays <= 0) {
            return;
        }
        List<Invoice> recent = invoiceRepository.findByBorrowerIdAndSubProgramIdOrderByCreatedAtDesc(
                invoice.getBorrowerId(), invoice.getSubProgramId());
        LocalDate today = LocalDate.now();
        for (Invoice prior : recent) {
            if (prior.getId().equals(invoice.getId())) {
                continue;
            }
            if (prior.getStatus() == null || !GAP_BLOCKING_STATUSES.contains(prior.getStatus())) {
                continue;
            }
            Instant created = prior.getCreatedAt();
            if (created == null) {
                continue;
            }
            LocalDate priorDate = created.atZone(ZoneId.systemDefault()).toLocalDate();
            long daysSince = ChronoUnit.DAYS.between(priorDate, today);
            if (daysSince < gapDays) {
                throw new RuntimeException(
                        "Minimum gap of " + gapDays + " days required since last financed/discounted invoice");
            }
            break;
        }
    }

    private static int parseIntParam(Object raw, int defaultValue) {
        if (raw instanceof Number n) {
            return n.intValue();
        }
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.toString().trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public Invoice getInvoice(UUID invoiceId) {
        return invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found: " + invoiceId));
    }

    /** Adjust PRUS/PIP amounts on an invoice (internal lending-service calls on PG success or settlement). */
    @Transactional
    public Invoice adjustPipAmounts(
            UUID invoiceId,
            BigDecimal principalAdd,
            BigDecimal principalSubtract,
            BigDecimal discountAdd,
            BigDecimal discountSubtract) {
        Invoice invoice = getInvoice(invoiceId);
        BigDecimal pip = invoice.getPipAmount() != null ? invoice.getPipAmount() : BigDecimal.ZERO;
        BigDecimal pipDisc =
                invoice.getPipDiscountAmount() != null ? invoice.getPipDiscountAmount() : BigDecimal.ZERO;
        if (principalAdd != null) {
            pip = pip.add(principalAdd);
        }
        if (principalSubtract != null) {
            pip = pip.subtract(principalSubtract);
            if (pip.compareTo(BigDecimal.ZERO) < 0) {
                pip = BigDecimal.ZERO;
            }
        }
        if (discountAdd != null) {
            pipDisc = pipDisc.add(discountAdd);
        }
        if (discountSubtract != null) {
            pipDisc = pipDisc.subtract(discountSubtract);
            if (pipDisc.compareTo(BigDecimal.ZERO) < 0) {
                pipDisc = BigDecimal.ZERO;
            }
        }
        invoice.setPipAmount(pip);
        invoice.setPipDiscountAmount(pipDisc);
        return invoiceRepository.save(invoice);
    }

    /**
     * Loads digital invoice bytes from object storage; callers must enforce authorization.
     *
     * @throws ResponseStatusException 404 with message {@code Digital invoice file not available} when metadata or bytes are missing
     */
    public DigitalInvoiceDownload loadDigitalInvoiceDownload(Invoice invoice) {
        String storageKey = invoice.getDigitalInvoiceStorageKey();
        if (storageKey == null || storageKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Digital invoice file not available");
        }
        Optional<byte[]> bytes = digitalInvoiceObjectStorage.tryDownload(storageKey);
        if (bytes.isEmpty()) {
            bytes = localDigitalInvoiceStorage.get(storageKey);
        }
        if (bytes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Digital invoice file not available");
        }
        String ct = invoice.getDigitalInvoiceContentType();
        if (ct == null || ct.isBlank()) {
            ct = "application/octet-stream";
        }
        String fn = invoice.getDigitalInvoiceFileName();
        if (fn == null || fn.isBlank()) {
            fn = "digital-invoice";
        }
        return new DigitalInvoiceDownload(bytes.get(), ct, fn);
    }

    public record DigitalInvoiceDownload(byte[] body, String contentType, String fileName) {}

    /**
     * Persists digital invoice metadata; uploads bytes to MinIO when {@code plp.storage.minio.enabled=true}.
     */
    @Transactional
    public InvoiceDigitalAttachmentResult attachDigitalInvoice(UUID invoiceId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("File is required");
        }
        Invoice invoice = getInvoice(invoiceId);
        String raw = file.getOriginalFilename();
        String safeName = sanitizeDigitalInvoiceFileName(raw != null ? raw : "invoice");
        String storageKey = "invoices/" + invoiceId + "/" + safeName;
        byte[] bytes = file.getBytes();
        String contentType = file.getContentType();

        try {
            localDigitalInvoiceStorage.put(storageKey, bytes);
        } catch (Exception e) {
            log.warn("Local digital invoice storage failed for {}: {}", storageKey, e.getMessage());
        }

        DigitalInvoiceObjectStorage.UploadAttempt attempt =
                digitalInvoiceObjectStorage.tryUpload(storageKey, bytes, contentType);

        invoice.setDigitalInvoiceFileName(safeName);
        invoice.setDigitalInvoiceContentType(contentType);
        invoice.setDigitalInvoiceStorageKey(storageKey);
        invoice.setDigitalInvoiceUploadedAt(Instant.now());
        invoiceRepository.save(invoice);

        if (attempt == DigitalInvoiceObjectStorage.UploadAttempt.UPLOADED) {
            return new InvoiceDigitalAttachmentResult("OBJECT_STORAGE", null);
        }
        if (localDigitalInvoiceStorage.get(storageKey).isPresent()) {
            return new InvoiceDigitalAttachmentResult("LOCAL_FILESYSTEM", null);
        }
        return switch (attempt) {
            case MINIO_DISABLED -> new InvoiceDigitalAttachmentResult("METADATA_ONLY",
                    "Digital invoice metadata saved; enable MinIO or check local storage path plp.storage.local.root.");
            case MINIO_MISCONFIGURED -> new InvoiceDigitalAttachmentResult("METADATA_ONLY",
                    "MinIO credentials missing. Configure plp.storage.minio or use local storage.");
            case UPLOAD_FAILED -> new InvoiceDigitalAttachmentResult("METADATA_ONLY",
                    "Upload failed. Check storage configuration and retry.");
            default -> new InvoiceDigitalAttachmentResult("METADATA_ONLY", "Digital invoice metadata saved.");
        };
    }

    public List<Invoice> getByAnchor(UUID anchorId) {
        return invoiceRepository.findByAnchorId(anchorId);
    }

    public List<Invoice> getByAnchorAndProgram(UUID anchorId, UUID programId) {
        return invoiceRepository.findByAnchorIdAndProgramId(anchorId, programId);
    }

    public List<Invoice> getByBorrower(UUID borrowerId) {
        return invoiceRepository.findByBorrowerId(borrowerId);
    }

    public List<Invoice> getByBorrowerEnriched(UUID borrowerId, String flowType) {
        String flowFilter = normalizeFlowFilter(flowType);
        return getByBorrower(borrowerId).stream()
                .filter(inv -> flowFilter == null || flowFilter.equalsIgnoreCase(String.valueOf(inv.getFlowType())))
                .peek(earlyPayEligibilityService::enrichInvoice)
                .toList();
    }

    public List<Invoice> getEligibleByBorrower(UUID borrowerId) {
        return invoiceRepository.findByBorrowerIdAndStatusIn(
                borrowerId,
                List.of(
                        "ELIGIBLE",
                        "BORROWER_ACCEPTED",
                        "PARTIALLY_DISCOUNTED",
                        InvoiceStatus.FINANCING_REQUESTED.name()));
    }

    public List<Invoice> getByProgram(UUID programId) {
        return invoiceRepository.findByProgramId(programId);
    }

    public Map<String, Object> listBorrowerInvoicesPaged(
            UUID borrowerId,
            String search,
            String status,
            String lifecycle,
            String flowType,
            String tab,
            int page,
            int size) {
        return paginateInvoices(getByBorrower(borrowerId), search, status, lifecycle, flowType, tab, page, size);
    }

    public Map<String, Object> listAnchorInvoicesPaged(
            UUID anchorId,
            UUID programId,
            String search,
            String status,
            String lifecycle,
            String flowType,
            String tab,
            int page,
            int size) {
        List<Invoice> base =
                programId != null ? getByAnchorAndProgram(anchorId, programId) : getByAnchor(anchorId);
        return paginateInvoices(base, search, status, lifecycle, flowType, tab, page, size);
    }

    public Map<String, Object> listInvoicesPaged(
            String search, String status, String lifecycle, String flowType, String tab, int page, int size) {
        return paginateInvoices(invoiceRepository.findAll(), search, status, lifecycle, flowType, tab, page, size);
    }

    public Map<String, Object> listBorrowerInvoicesPaged(
            UUID borrowerId, String search, String status, String lifecycle, int page, int size) {
        return listBorrowerInvoicesPaged(borrowerId, search, status, lifecycle, null, null, page, size);
    }

    public Map<String, Object> listAnchorInvoicesPaged(
            UUID anchorId, UUID programId, String search, String status, String lifecycle, int page, int size) {
        return listAnchorInvoicesPaged(anchorId, programId, search, status, lifecycle, null, null, page, size);
    }

    public Map<String, Object> listInvoicesPaged(String search, String status, String lifecycle, int page, int size) {
        return listInvoicesPaged(search, status, lifecycle, null, null, page, size);
    }

    private Map<String, Object> paginateInvoices(
            List<Invoice> source,
            String search,
            String status,
            String lifecycle,
            String flowType,
            String tab,
            int page,
            int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        String q = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        String st = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        String flowFilter = normalizeFlowFilter(flowType);
        String tabFilter = tab == null || tab.isBlank() ? null : tab.trim().toLowerCase(Locale.ROOT);

        List<Invoice> filtered = source.stream()
                .filter(inv -> flowFilter == null || flowFilter.equalsIgnoreCase(String.valueOf(inv.getFlowType())))
                .filter(inv -> matchesSellerInitiatedTab(inv, tabFilter))
                .filter(inv -> st == null || st.equalsIgnoreCase(String.valueOf(inv.getStatus())))
                .filter(inv -> InvoiceLifecycleFilter.matchesLifecycle(inv.getStatus(), lifecycle))
                .filter(inv -> {
                    if (q.isEmpty()) return true;
                    String num = inv.getInvoiceNumber() == null ? "" : inv.getInvoiceNumber().toLowerCase(Locale.ROOT);
                    String borrower = inv.getBorrowerId() == null ? "" : inv.getBorrowerId().toString().toLowerCase(Locale.ROOT);
                    return num.contains(q) || borrower.contains(q);
                })
                .sorted((a, b) -> {
                    Instant ca = a.getCreatedAt();
                    Instant cb = b.getCreatedAt();
                    if (ca == null && cb == null) return 0;
                    if (ca == null) return 1;
                    if (cb == null) return -1;
                    return cb.compareTo(ca);
                })
                .toList();

        int total = filtered.size();
        int from = Math.min(safePage * safeSize, total);
        int to = Math.min(from + safeSize, total);
        List<Invoice> slice = filtered.subList(from, to);
        slice.forEach(earlyPayEligibilityService::enrichInvoice);
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / safeSize);

        Map<String, Object> pageMeta = new LinkedHashMap<>();
        pageMeta.put("number", safePage);
        pageMeta.put("size", safeSize);
        pageMeta.put("totalElements", total);
        pageMeta.put("totalPages", totalPages);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("data", slice);
        out.put("page", pageMeta);
        return out;
    }

    private static String sanitizeDigitalInvoiceFileName(String name) {
        String base = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (base.length() > 180) {
            base = base.substring(0, 180);
        }
        return base.isBlank() ? "invoice.bin" : base;
    }

    private void assertNoDuplicateInvoice(Invoice invoice) {
        if (invoice.getBorrowerId() == null
                || invoice.getInvoiceNumber() == null
                || invoice.getInvoiceDate() == null
                || invoice.getDueDate() == null) {
            return;
        }
        findDuplicateInvoice(
                        invoice.getBorrowerId(),
                        invoice.getInvoiceNumber(),
                        invoice.getInvoiceDate(),
                        invoice.getDueDate())
                .ifPresent(existing -> {
                    throw new RuntimeException(String.format(
                            "Duplicate invoice for this borrower: number %s, invoice date %s, due date %s",
                            invoice.getInvoiceNumber(), invoice.getInvoiceDate(), invoice.getDueDate()));
                });
    }

    private Optional<Invoice> findDuplicateInvoice(
            UUID borrowerId, String invoiceNumber, LocalDate invoiceDate, LocalDate dueDate) {
        return invoiceRepository.findByBorrowerIdAndInvoiceNumberAndInvoiceDateAndDueDate(
                borrowerId, invoiceNumber, invoiceDate, dueDate);
    }

    private void validateInvoice(Invoice invoice) {
        if (invoice.getInvoiceNumber() == null || invoice.getInvoiceNumber().isBlank()) {
            throw new RuntimeException("Invoice number is required");
        }
        if (invoice.getInvoiceAmount() == null || invoice.getInvoiceAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Invoice amount must be positive");
        }
        if (invoice.getInvoiceDate() != null
                && invoice.getDueDate() != null
                && invoice.getDueDate().isBefore(invoice.getInvoiceDate())) {
            throw new RuntimeException("Due date cannot be before invoice date");
        }
        assertNoDuplicateInvoice(invoice);

        borrowerRepository.findById(invoice.getBorrowerId())
                .orElseThrow(() -> new RuntimeException("Borrower not found: " + invoice.getBorrowerId()));
    }

    private void applySubProgramLinkForCreate(Invoice invoice) {
        if (invoice.getSubProgramId() == null) {
            return;
        }
        SubProgram sub = subProgramRepository.findById(invoice.getSubProgramId())
                .orElseThrow(() -> new RuntimeException("Sub program not found: " + invoice.getSubProgramId()));
        if (invoice.getAnchorId() == null && sub.getAnchorId() != null) {
            invoice.setAnchorId(sub.getAnchorId());
        }
        if (invoice.getProgramId() == null && sub.getProgramId() != null) {
            invoice.setProgramId(sub.getProgramId());
        }
        validateInvoiceAgainstSubProgram(sub, invoice.getProgramId(), invoice.getAnchorId(), invoice.getBorrowerId());
        invoice.setFlowType(sub.getFlowType());
    }

    private Optional<Borrower> resolveCsvBorrower(
            String borrowerCode, String partyCode, UUID subProgramId, UUID anchorId) {
        if (partyCode != null && !partyCode.isBlank()) {
            if (subProgramId == null) {
                return Optional.empty();
            }
            return subProgramBorrowerRepository
                    .findBySubProgramIdAndPartyCodeIgnoreCase(subProgramId, partyCode.trim())
                    .flatMap(m -> borrowerRepository.findById(m.getBorrowerId()));
        }
        if (borrowerCode != null && !borrowerCode.isBlank()) {
            return borrowerRepository.findByBorrowerCode(borrowerCode.trim());
        }
        return Optional.empty();
    }

    private void validateInvoiceAgainstSubProgram(SubProgram sub, UUID programId, UUID anchorId, UUID borrowerId) {
        if (!Objects.equals(sub.getProgramId(), programId)) {
            throw new RuntimeException("Sub program program_id does not match invoice programId");
        }
        if (sub.getAnchorId() != null && !Objects.equals(sub.getAnchorId(), anchorId)) {
            throw new RuntimeException("Sub program anchor_id does not match invoice anchorId");
        }
        subProgramBorrowerRepository.findBySubProgramIdAndBorrowerId(sub.getId(), borrowerId)
                .orElseThrow(() -> new RuntimeException("Borrower is not in sub program borrower group"));
    }

    private void applyFlowTypeDefaultOrValidate(Invoice invoice) {
        String ft = invoice.getFlowType();
        if (ft == null || ft.isBlank()) {
            invoice.setFlowType(FLOW_PURCHASE_BILL_DISCOUNTING);
            return;
        }
        String n = ft.trim().toUpperCase(Locale.ROOT);
        if (FLOW_PURCHASE_BILL_DISCOUNTING.equals(n) || FLOW_SALES_BILL_DISCOUNTING.equals(n)
                || InvoiceDiscountingFlowType.PURCHASE_ORDER_DISCOUNTING.equals(n)) {
            invoice.setFlowType(n);
            return;
        }
        throw new RuntimeException("Invalid flowType: " + ft + ". Use " + FLOW_PURCHASE_BILL_DISCOUNTING + ", "
                + FLOW_SALES_BILL_DISCOUNTING + ", or " + InvoiceDiscountingFlowType.PURCHASE_ORDER_DISCOUNTING);
    }

    private static void requirePurchaseBillFlow(Invoice invoice, String action) {
        if (!InvoiceDiscountingFlowType.isPurchaseBill(invoice.getFlowType())) {
            throw new RuntimeException("Invoice " + action + " is only allowed for PURCHASE_BILL_DISCOUNTING invoices");
        }
    }

    private static void requireSellerInitiatedFlow(Invoice invoice, String action) {
        if (!InvoiceDiscountingFlowType.isSellerInitiated(invoice.getFlowType())) {
            throw new RuntimeException(
                    "Invoice " + action + " is only allowed for SALES_BILL_DISCOUNTING or PURCHASE_ORDER_DISCOUNTING");
        }
    }

    private static String normalizeFlowFilter(String flowType) {
        if (flowType == null || flowType.isBlank()) {
            return null;
        }
        String n = flowType.trim().toUpperCase(Locale.ROOT);
        InvoiceDiscountingFlowType.requireKnown(n);
        return n;
    }

    private static boolean matchesSellerInitiatedTab(Invoice inv, String tab) {
        if (tab == null) {
            return true;
        }
        String status = inv.getStatus() == null ? "" : inv.getStatus().toUpperCase(Locale.ROOT);
        return switch (tab) {
            case "pending" -> "UPLOADED".equals(status);
            case "approved" -> !"UPLOADED".equals(status)
                    && !InvoiceStatus.REJECTED.name().equals(status)
                    && InvoiceLifecycleFilter.matchesLifecycle(status, "active");
            case "rejected" -> InvoiceStatus.REJECTED.name().equals(status);
            default -> true;
        };
    }

    /**
     * Header keys after normalizing: lowercase, underscores removed (so {@code flow_type} matches {@code flowtype}).
     */
    private static String normalizeCsvCell(String value) {
        if (value == null) {
            return "";
        }
        String s = value.trim();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            s = s.substring(1, s.length() - 1).trim();
        }
        return s;
    }

    /** Accepts ISO and common Excel/regional date formats in invoice CSV uploads. */
    private static LocalDate parseCsvLocalDate(String raw) {
        String s = normalizeCsvCell(raw);
        if (s.isEmpty()) {
            return null;
        }
        int space = s.indexOf(' ');
        if (space > 0) {
            s = s.substring(0, space).trim();
        }
        int t = s.indexOf('T');
        if (t > 0) {
            s = s.substring(0, t).trim();
        }
        for (DateTimeFormatter fmt : CSV_DATE_FORMATS) {
            try {
                return LocalDate.parse(s, fmt);
            } catch (DateTimeParseException ignored) {
                // try next pattern
            }
        }
        return null;
    }

    private static Map<String, Integer> parseInvoiceCsvHeader(String headerLine) {
        String line = headerLine;
        if (line.startsWith("\uFEFF")) {
            line = line.substring(1);
        }
        String[] headers = line.split(",", -1);
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            String key = normalizeCsvCell(headers[i]).toLowerCase(Locale.ROOT).replace("_", "");
            map.putIfAbsent(key, i);
        }
        return map;
    }

    private static boolean hasNamedInvoiceColumns(Map<String, Integer> idx) {
        boolean hasCounterparty = idx.containsKey("borrowercode") || idx.containsKey("partycode") || idx.containsKey("buyercode");
        return idx.containsKey("invoicenumber") && hasCounterparty && idx.containsKey("invoicedate")
                && idx.containsKey("duedate") && idx.containsKey("invoiceamount") && idx.containsKey("taxamount");
    }

    private record CsvInvoiceRow(
            String invoiceNumber,
            String borrowerCode,
            String partyCode,
            String invoiceDateStr,
            String dueDateStr,
            String invoiceAmtStr,
            String taxAmtStr,
            String flowRaw,
            String subProgramCode,
            String subProgramIdRaw
    ) {}

    /** CSV sub-program resolution: {@code subProgramId} column wins over {@code subProgramCode} when both are set. */
    private record CsvSubProgramPick(UUID subProgramId, boolean skipRow) {}

    /**
     * Prefer header-driven columns when the header row names the six core fields; otherwise legacy column order
     * (optional 7th = flow type, 8th = sub program code, 9th = sub program id).
     */
    private static CsvInvoiceRow extractInvoiceCsvRow(String[] cols, Map<String, Integer> headerIndex, int rowNum) {
        if (hasNamedInvoiceColumns(headerIndex)) {
            Integer inv = headerIndex.get("invoicenumber");
            Integer bc = headerIndex.get("borrowercode");
            Integer pc = headerIndex.get("partycode");
            if (pc == null) {
                pc = headerIndex.get("buyercode");
            }
            Integer id = headerIndex.get("invoicedate");
            Integer dd = headerIndex.get("duedate");
            Integer ia = headerIndex.get("invoiceamount");
            Integer ta = headerIndex.get("taxamount");
            Integer maxIdx = maxIndex(inv, bc, pc, id, dd, ia, ta);
            Integer flowIx = headerIndex.get("flowtype");
            Integer subCodeIx = headerIndex.get("subprogramcode");
            Integer subIdIx = headerIndex.get("subprogramid");
            if (maxIdx == null || inv == null || id == null || dd == null || ia == null || ta == null) {
                log.warn("Skipping row {}: invalid invoice CSV header indices", rowNum);
                return null;
            }
            int requiredCols = maxIdx + 1;
            Integer optionalMax = maxIndex(flowIx, subCodeIx, subIdIx);
            if (optionalMax != null && optionalMax + 1 > requiredCols) {
                requiredCols = optionalMax + 1;
            }
            if (cols.length < requiredCols) {
                log.warn("Skipping row {}: insufficient columns for named header layout", rowNum);
                return null;
            }
            String borrowerCode = bc != null && bc < cols.length ? normalizeCsvCell(cols[bc]) : "";
            String partyCode = pc != null && pc < cols.length ? normalizeCsvCell(cols[pc]) : "";
            String flowRaw = "";
            if (flowIx != null && flowIx < cols.length) {
                flowRaw = normalizeCsvCell(cols[flowIx]);
            }
            String subProgramCode = "";
            if (subCodeIx != null && subCodeIx < cols.length) {
                subProgramCode = normalizeCsvCell(cols[subCodeIx]);
            }
            String subProgramIdRaw = "";
            if (subIdIx != null && subIdIx < cols.length) {
                subProgramIdRaw = normalizeCsvCell(cols[subIdIx]);
            }
            return new CsvInvoiceRow(
                    normalizeCsvCell(cols[inv]),
                    borrowerCode,
                    partyCode,
                    normalizeCsvCell(cols[id]),
                    normalizeCsvCell(cols[dd]),
                    normalizeCsvCell(cols[ia]).replace(",", ""),
                    normalizeCsvCell(cols[ta]).replace(",", ""),
                    flowRaw,
                    subProgramCode,
                    subProgramIdRaw);
        }
        if (cols.length < 6) {
            return null;
        }
        String flowRaw = cols.length > 6 ? normalizeCsvCell(cols[6]) : "";
        String subCodeLegacy = cols.length > 7 ? normalizeCsvCell(cols[7]) : "";
        String subIdLegacy = cols.length > 8 ? normalizeCsvCell(cols[8]) : "";
        return new CsvInvoiceRow(
                normalizeCsvCell(cols[0]),
                normalizeCsvCell(cols[1]),
                "",
                normalizeCsvCell(cols[2]),
                normalizeCsvCell(cols[3]),
                normalizeCsvCell(cols[4]).replace(",", ""),
                normalizeCsvCell(cols[5]).replace(",", ""),
                flowRaw,
                subCodeLegacy,
                subIdLegacy);
    }

    private static Integer maxIndex(Integer... indices) {
        Integer m = null;
        for (Integer i : indices) {
            if (i == null) {
                continue;
            }
            if (m == null || i > m) {
                m = i;
            }
        }
        return m;
    }

    /** @return resolved canonical flow type, or null if row should be skipped (anchor CSV: PBF only) */
    private static String resolveFlowTypeForCsv(String flowRaw, int rowNum) {
        if (flowRaw == null || flowRaw.isBlank()) {
            return FLOW_PURCHASE_BILL_DISCOUNTING;
        }
        String n = flowRaw.trim().toUpperCase(Locale.ROOT);
        if (FLOW_PURCHASE_BILL_DISCOUNTING.equals(n)) {
            return n;
        }
        log.warn("Skipping row {}: invalid flowType '{}' (anchor CSV allows PURCHASE_BILL_DISCOUNTING only)", rowNum, flowRaw);
        return null;
    }

    private CsvSubProgramPick pickCsvSubProgram(String codeRaw, String idRaw, int rowNum) {
        String idTrim = idRaw != null ? idRaw.trim() : "";
        String codeTrim = codeRaw != null ? codeRaw.trim() : "";
        if (!idTrim.isEmpty()) {
            try {
                return new CsvSubProgramPick(UUID.fromString(idTrim), false);
            } catch (IllegalArgumentException e) {
                log.warn("Skipping row {}: invalid subProgramId", rowNum);
                return new CsvSubProgramPick(null, true);
            }
        }
        if (!codeTrim.isEmpty()) {
            Optional<SubProgram> sub = subProgramRepository.findByCode(codeTrim);
            if (sub.isEmpty()) {
                log.warn("Skipping row {}: subProgramCode not found: {}", rowNum, codeTrim);
                return new CsvSubProgramPick(null, true);
            }
            return new CsvSubProgramPick(sub.get().getId(), false);
        }
        return new CsvSubProgramPick(null, false);
    }

    private void computeEligibleAmount(Invoice invoice) {
        Program program = programRepository.findById(invoice.getProgramId())
                .orElseThrow(() -> new RuntimeException("Program not found: " + invoice.getProgramId()));

        BigDecimal marginPct = invoice.getMarginPercent() != null
                ? invoice.getMarginPercent()
                : (program.getMarginPercent() != null ? program.getMarginPercent() : BigDecimal.ZERO);
        invoice.setMarginPercent(marginPct);

        BigDecimal netAmount = invoice.getNetAmount() != null
                ? invoice.getNetAmount()
                : invoice.getInvoiceAmount().add(invoice.getTaxAmount() != null ? invoice.getTaxAmount() : BigDecimal.ZERO);
        invoice.setNetAmount(netAmount);

        BigDecimal eligible = netAmount
                .multiply(new BigDecimal("100").subtract(marginPct))
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        invoice.setEligibleAmount(eligible);
        invoice.setAvailableAmount(eligible.subtract(
                invoice.getDiscountedAmount() != null ? invoice.getDiscountedAmount() : BigDecimal.ZERO));
    }

    private void recordStatusChange(Invoice invoice, String previousStatus, String message) {
        if (previousStatus == null || previousStatus.equals(invoice.getStatus())) {
            return;
        }
        entityAuditHelper.captureInvoiceStatusChange(
                invoice.getId().toString(),
                invoice.getInvoiceNumber(),
                previousStatus,
                invoice.getStatus(),
                invoice.getUploadedByUserId() != null ? invoice.getUploadedByUserId().toString() : null,
                null,
                message);
    }
}
