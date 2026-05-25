package com.plp.program.service;

import com.plp.program.model.dto.InvoiceDigitalAttachmentResult;
import com.plp.program.model.entity.Borrower;
import com.plp.program.model.entity.Invoice;
import com.plp.program.model.entity.Program;
import com.plp.program.model.entity.SubProgram;
import com.plp.program.model.enums.InvoiceStatus;
import com.plp.program.repository.BorrowerRepository;
import com.plp.program.repository.InvoiceRepository;
import com.plp.program.repository.ProgramRepository;
import com.plp.program.repository.SubProgramBorrowerRepository;
import com.plp.program.repository.SubProgramRepository;
import com.plp.program.storage.DigitalInvoiceObjectStorage;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final String FLOW_PURCHASE_BILL_DISCOUNTING = "PURCHASE_BILL_DISCOUNTING";
    private static final String FLOW_SALES_BILL_DISCOUNTING = "SALES_BILL_DISCOUNTING";

    @Transactional
    public Invoice createInvoice(Invoice invoice) {
        return createInvoice(invoice, null);
    }

    @Transactional
    public Invoice createInvoice(Invoice invoice, UUID uploadedByUserId) {
        validateInvoice(invoice);
        applySubProgramLinkForCreate(invoice);
        if (invoice.getSubProgramId() == null) {
            applyFlowTypeDefaultOrValidate(invoice);
        }
        computeEligibleAmount(invoice);
        invoice.setStatus("UPLOADED");
        invoice.setSource("MANUAL");
        invoice.setUploadedByUserId(uploadedByUserId);
        invoice = invoiceRepository.save(invoice);
        log.info("Invoice created: {} anchor={} borrower={} subProgram={} amount={}",
                invoice.getInvoiceNumber(), invoice.getAnchorId(), invoice.getBorrowerId(),
                invoice.getSubProgramId(), invoice.getInvoiceAmount());
        return invoice;
    }

    @Transactional
    public List<Invoice> uploadInvoiceCsv(UUID anchorId, UUID programId, InputStream csvStream, UUID uploadedByUserId) {
        Program program = programRepository.findById(programId)
                .orElseThrow(() -> new RuntimeException("Program not found: " + programId));

        BigDecimal marginPct = program.getMarginPercent() != null ? program.getMarginPercent() : new BigDecimal("10.00");

        List<Invoice> results = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(csvStream))) {
            String header = reader.readLine();
            if (header == null) {
                throw new RuntimeException("CSV file is empty");
            }

            Map<String, Integer> headerIndex = parseInvoiceCsvHeader(header);

            String line;
            int rowNum = 1;
            while ((line = reader.readLine()) != null) {
                rowNum++;
                String[] cols = line.split(",", -1);
                if (cols.length < 6) {
                    log.warn("Skipping row {}: insufficient columns (need 6: invoiceNumber,borrowerCode,invoiceDate,dueDate,invoiceAmount,taxAmount)", rowNum);
                    continue;
                }

                CsvInvoiceRow parsed = extractInvoiceCsvRow(cols, headerIndex, rowNum);
                if (parsed == null) {
                    continue;
                }

                String invoiceNumber = parsed.invoiceNumber();
                String borrowerCode = parsed.borrowerCode();
                String invoiceDateStr = parsed.invoiceDateStr();
                String dueDateStr = parsed.dueDateStr();
                String invoiceAmtStr = parsed.invoiceAmtStr();
                String taxAmtStr = parsed.taxAmtStr();
                String flowRaw = parsed.flowRaw();
                String subProgramCode = parsed.subProgramCode();
                String subProgramIdRaw = parsed.subProgramIdRaw();

                if (invoiceNumber.isEmpty() || borrowerCode.isEmpty()) {
                    log.warn("Skipping row {}: missing invoiceNumber or borrowerCode", rowNum);
                    continue;
                }

                Optional<Invoice> existing = invoiceRepository.findByInvoiceNumberAndAnchorId(invoiceNumber, anchorId);
                if (existing.isPresent()) {
                    log.warn("Skipping row {}: duplicate invoice {} for anchor {}", rowNum, invoiceNumber, anchorId);
                    continue;
                }

                Optional<Borrower> borrowerOpt = borrowerRepository.findByBorrowerCode(borrowerCode);
                if (borrowerOpt.isEmpty()) {
                    log.warn("Skipping row {}: borrower not found: {}", rowNum, borrowerCode);
                    continue;
                }

                BigDecimal invoiceAmt;
                BigDecimal taxAmt;
                try {
                    invoiceAmt = new BigDecimal(invoiceAmtStr);
                    taxAmt = taxAmtStr.isEmpty() ? BigDecimal.ZERO : new BigDecimal(taxAmtStr);
                } catch (NumberFormatException e) {
                    log.warn("Skipping row {}: invalid amount", rowNum);
                    continue;
                }

                if (invoiceAmt.compareTo(BigDecimal.ZERO) <= 0) {
                    log.warn("Skipping row {}: invoice amount must be positive", rowNum);
                    continue;
                }

                LocalDate invoiceDate;
                LocalDate dueDate;
                try {
                    invoiceDate = LocalDate.parse(invoiceDateStr, DATE_FMT);
                    dueDate = LocalDate.parse(dueDateStr, DATE_FMT);
                } catch (Exception e) {
                    log.warn("Skipping row {}: invalid date format (use yyyy-MM-dd)", rowNum);
                    continue;
                }

                BigDecimal netAmount = invoiceAmt.add(taxAmt);

                CsvSubProgramPick subPick = pickCsvSubProgram(subProgramCode, subProgramIdRaw, rowNum);
                if (subPick.skipRow()) {
                    continue;
                }
                UUID csvSubProgramId = subPick.subProgramId();
                SubProgram linkedSub = null;
                if (csvSubProgramId != null) {
                    linkedSub = subProgramRepository.findById(csvSubProgramId).orElse(null);
                    if (linkedSub == null) {
                        log.warn("Skipping row {}: sub program not found: {}", rowNum, csvSubProgramId);
                        continue;
                    }
                    try {
                        validateInvoiceAgainstSubProgram(linkedSub, programId, anchorId, borrowerOpt.get().getId());
                    } catch (RuntimeException e) {
                        log.warn("Skipping row {}: {}", rowNum, e.getMessage());
                        continue;
                    }
                }

                final String resolvedFlowType;
                final UUID invoiceSubProgramId;
                if (linkedSub != null) {
                    resolvedFlowType = linkedSub.getFlowType();
                    invoiceSubProgramId = linkedSub.getId();
                } else {
                    String rft = resolveFlowTypeForCsv(flowRaw, rowNum);
                    if (rft == null) {
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

        log.info("Invoice CSV upload: anchor={} program={} rows={}", anchorId, programId, results.size());
        return results;
    }

    @Transactional
    public Invoice verifyInvoice(UUID invoiceId, UUID verifiedBy) {
        Invoice invoice = getInvoice(invoiceId);
        invoice.setVerified(true);
        invoice.setVerifiedAt(Instant.now());
        invoice.setVerifiedBy(verifiedBy);
        if ("UPLOADED".equals(invoice.getStatus())) {
            invoice.setStatus("VERIFIED");
        }
        return invoiceRepository.save(invoice);
    }

    @Transactional
    public Invoice confirmInvoice(UUID invoiceId) {
        Invoice invoice = getInvoice(invoiceId);
        if (!"VERIFIED".equals(invoice.getStatus())) {
            throw new RuntimeException(
                    "Invoice must be in VERIFIED state before anchor confirmation. Current status: " + invoice.getStatus());
        }
        invoice.setAnchorConfirmed(true);
        invoice.setAnchorConfirmedAt(Instant.now());
        invoice.setStatus("ELIGIBLE");
        return invoiceRepository.save(invoice);
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
        boolean purchaseFlow = flow == null || flow.isBlank() || FLOW_PURCHASE_BILL_DISCOUNTING.equals(flow);
        if (!purchaseFlow) {
            throw new RuntimeException("Borrower acceptance does not apply to SALES_BILL_DISCOUNTING invoices");
        }
        if (!"ELIGIBLE".equals(invoice.getStatus())) {
            throw new RuntimeException(
                    "Borrower acceptance allowed only when invoice status is ELIGIBLE. Current status: " + invoice.getStatus());
        }
        invoice.setBorrowerAccepted(true);
        invoice.setBorrowerAcceptedAt(Instant.now());
        invoice.setStatus("BORROWER_ACCEPTED");
        return invoiceRepository.save(invoice);
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
        String flow = invoice.getFlowType();
        boolean purchaseFlow = flow == null || flow.isBlank() || FLOW_PURCHASE_BILL_DISCOUNTING.equals(flow);
        boolean salesFlow = FLOW_SALES_BILL_DISCOUNTING.equals(flow);
        boolean ok;
        if (purchaseFlow) {
            ok = "BORROWER_ACCEPTED".equals(current) || "PARTIALLY_DISCOUNTED".equals(current);
        } else if (salesFlow) {
            ok = "ELIGIBLE".equals(current) || "PARTIALLY_DISCOUNTED".equals(current);
        } else {
            ok = false;
        }
        if (!ok) {
            throw new RuntimeException("Invoice cannot transition to FINANCING_REQUESTED from status: " + current);
        }
        log.info("Updating invoice {} status from {} to FINANCING_REQUESTED", invoiceId, current);
        invoice.setStatus(InvoiceStatus.FINANCING_REQUESTED.name());
        return invoiceRepository.save(invoice);
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
        boolean salesFlow = FLOW_SALES_BILL_DISCOUNTING.equals(flow);
        BigDecimal disc = invoice.getDiscountedAmount() != null ? invoice.getDiscountedAmount() : BigDecimal.ZERO;
        boolean partial = disc.compareTo(BigDecimal.ZERO) > 0;
        if (purchaseFlow) {
            invoice.setStatus(partial ? "PARTIALLY_DISCOUNTED" : "BORROWER_ACCEPTED");
        } else if (salesFlow) {
            invoice.setStatus(partial ? "PARTIALLY_DISCOUNTED" : "ELIGIBLE");
        } else {
            throw new RuntimeException("Cannot revert FINANCING_REQUESTED for flowType: " + flow);
        }
        log.info("Reverted invoice {} from FINANCING_REQUESTED to {}", invoiceId, invoice.getStatus());
        return invoiceRepository.save(invoice);
    }

    @Transactional
    public Invoice markDiscounted(UUID invoiceId, BigDecimal discountedAmount) {
        Invoice invoice = invoiceRepository.findByIdForUpdate(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found: " + invoiceId));
        BigDecimal newDiscounted = invoice.getDiscountedAmount().add(discountedAmount);
        invoice.setDiscountedAmount(newDiscounted);
        invoice.setAvailableAmount(invoice.getEligibleAmount().subtract(newDiscounted));
        if (invoice.getAvailableAmount().compareTo(BigDecimal.ZERO) <= 0) {
            invoice.setAvailableAmount(BigDecimal.ZERO);
            invoice.setStatus("FULLY_DISCOUNTED");
        } else {
            invoice.setStatus("PARTIALLY_DISCOUNTED");
        }
        return invoiceRepository.save(invoice);
    }

    public Invoice getInvoice(UUID invoiceId) {
        return invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found: " + invoiceId));
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

        DigitalInvoiceObjectStorage.UploadAttempt attempt =
                digitalInvoiceObjectStorage.tryUpload(storageKey, bytes, contentType);

        invoice.setDigitalInvoiceFileName(safeName);
        invoice.setDigitalInvoiceContentType(contentType);
        invoice.setDigitalInvoiceStorageKey(storageKey);
        invoice.setDigitalInvoiceUploadedAt(Instant.now());
        invoiceRepository.save(invoice);

        return switch (attempt) {
            case UPLOADED -> new InvoiceDigitalAttachmentResult("OBJECT_STORAGE", null);
            case MINIO_DISABLED -> new InvoiceDigitalAttachmentResult("METADATA_ONLY",
                    "TODO: Enable plp.storage.minio.enabled and MINIO_ACCESS_KEY/MINIO_SECRET_KEY so bytes are stored in MinIO (bucket from plp.storage.minio.bucket). Metadata and key invoices/{invoiceId}/{fileName} are saved.");
            case MINIO_MISCONFIGURED -> new InvoiceDigitalAttachmentResult("METADATA_ONLY",
                    "TODO: MinIO is enabled but access-key or secret-key is empty. Metadata saved; configure plp.storage.minio credentials.");
            case UPLOAD_FAILED -> new InvoiceDigitalAttachmentResult("METADATA_ONLY",
                    "TODO: MinIO upload failed (endpoint, bucket, or network). Metadata saved; verify docker-compose MinIO and retry.");
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

    private static String sanitizeDigitalInvoiceFileName(String name) {
        String base = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (base.length() > 180) {
            base = base.substring(0, 180);
        }
        return base.isBlank() ? "invoice.bin" : base;
    }

    private void validateInvoice(Invoice invoice) {
        if (invoice.getInvoiceNumber() == null || invoice.getInvoiceNumber().isBlank()) {
            throw new RuntimeException("Invoice number is required");
        }
        if (invoice.getInvoiceAmount() == null || invoice.getInvoiceAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Invoice amount must be positive");
        }
        if (invoice.getDueDate() != null && invoice.getDueDate().isBefore(LocalDate.now())) {
            throw new RuntimeException("Invoice due date cannot be in the past");
        }

        Optional<Invoice> existing = invoiceRepository.findByInvoiceNumberAndAnchorId(
                invoice.getInvoiceNumber(), invoice.getAnchorId());
        if (existing.isPresent()) {
            throw new RuntimeException("Duplicate invoice number: " + invoice.getInvoiceNumber() + " for this anchor");
        }

        borrowerRepository.findById(invoice.getBorrowerId())
                .orElseThrow(() -> new RuntimeException("Borrower not found: " + invoice.getBorrowerId()));
    }

    private void applySubProgramLinkForCreate(Invoice invoice) {
        if (invoice.getSubProgramId() == null) {
            return;
        }
        SubProgram sub = subProgramRepository.findById(invoice.getSubProgramId())
                .orElseThrow(() -> new RuntimeException("Sub program not found: " + invoice.getSubProgramId()));
        validateInvoiceAgainstSubProgram(sub, invoice.getProgramId(), invoice.getAnchorId(), invoice.getBorrowerId());
        invoice.setFlowType(sub.getFlowType());
    }

    private void validateInvoiceAgainstSubProgram(SubProgram sub, UUID programId, UUID anchorId, UUID borrowerId) {
        if (!sub.getProgramId().equals(programId)) {
            throw new RuntimeException("Sub program program_id does not match invoice programId");
        }
        if (!sub.getAnchorId().equals(anchorId)) {
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
        if (FLOW_PURCHASE_BILL_DISCOUNTING.equals(n) || FLOW_SALES_BILL_DISCOUNTING.equals(n)) {
            invoice.setFlowType(n);
            return;
        }
        throw new RuntimeException("Invalid flowType: " + ft + ". Use " + FLOW_PURCHASE_BILL_DISCOUNTING + " or "
                + FLOW_SALES_BILL_DISCOUNTING);
    }

    /**
     * Header keys after normalizing: lowercase, underscores removed (so {@code flow_type} matches {@code flowtype}).
     */
    private static Map<String, Integer> parseInvoiceCsvHeader(String headerLine) {
        String[] headers = headerLine.split(",", -1);
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            String key = headers[i].trim().toLowerCase(Locale.ROOT).replace("_", "");
            map.putIfAbsent(key, i);
        }
        return map;
    }

    private static boolean hasNamedInvoiceColumns(Map<String, Integer> idx) {
        return idx.containsKey("invoicenumber") && idx.containsKey("borrowercode") && idx.containsKey("invoicedate")
                && idx.containsKey("duedate") && idx.containsKey("invoiceamount") && idx.containsKey("taxamount");
    }

    private record CsvInvoiceRow(
            String invoiceNumber,
            String borrowerCode,
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
            Integer id = headerIndex.get("invoicedate");
            Integer dd = headerIndex.get("duedate");
            Integer ia = headerIndex.get("invoiceamount");
            Integer ta = headerIndex.get("taxamount");
            Integer maxIdx = maxIndex(inv, bc, id, dd, ia, ta);
            Integer flowIx = headerIndex.get("flowtype");
            Integer subCodeIx = headerIndex.get("subprogramcode");
            Integer subIdIx = headerIndex.get("subprogramid");
            if (maxIdx == null) {
                log.warn("Skipping row {}: invalid invoice CSV header indices", rowNum);
                return null;
            }
            int requiredCols = maxIdx + 1;
            for (Integer ix : List.of(flowIx, subCodeIx, subIdIx)) {
                if (ix != null && ix + 1 > requiredCols) {
                    requiredCols = ix + 1;
                }
            }
            if (cols.length < requiredCols) {
                log.warn("Skipping row {}: insufficient columns for named header layout", rowNum);
                return null;
            }
            String flowRaw = "";
            if (flowIx != null && flowIx < cols.length) {
                flowRaw = cols[flowIx].trim();
            }
            String subProgramCode = "";
            if (subCodeIx != null && subCodeIx < cols.length) {
                subProgramCode = cols[subCodeIx].trim();
            }
            String subProgramIdRaw = "";
            if (subIdIx != null && subIdIx < cols.length) {
                subProgramIdRaw = cols[subIdIx].trim();
            }
            return new CsvInvoiceRow(
                    cols[inv].trim(),
                    cols[bc].trim(),
                    cols[id].trim(),
                    cols[dd].trim(),
                    cols[ia].trim().replace(",", ""),
                    cols[ta].trim().replace(",", ""),
                    flowRaw,
                    subProgramCode,
                    subProgramIdRaw);
        }
        if (cols.length < 6) {
            return null;
        }
        String flowRaw = cols.length > 6 ? cols[6].trim() : "";
        String subCodeLegacy = cols.length > 7 ? cols[7].trim() : "";
        String subIdLegacy = cols.length > 8 ? cols[8].trim() : "";
        return new CsvInvoiceRow(
                cols[0].trim(),
                cols[1].trim(),
                cols[2].trim(),
                cols[3].trim(),
                cols[4].trim().replace(",", ""),
                cols[5].trim().replace(",", ""),
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

    /** @return resolved canonical flow type, or null if row should be skipped */
    private static String resolveFlowTypeForCsv(String flowRaw, int rowNum) {
        if (flowRaw == null || flowRaw.isBlank()) {
            return FLOW_PURCHASE_BILL_DISCOUNTING;
        }
        String n = flowRaw.trim().toUpperCase(Locale.ROOT);
        if (FLOW_PURCHASE_BILL_DISCOUNTING.equals(n) || FLOW_SALES_BILL_DISCOUNTING.equals(n)) {
            return n;
        }
        log.warn("Skipping row {}: invalid flowType '{}'", rowNum, flowRaw);
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
                : (program.getMarginPercent() != null ? program.getMarginPercent() : new BigDecimal("10.00"));
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
}
