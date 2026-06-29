package com.plp.program.service.earlypay;

import com.plp.program.model.dto.EarlyPayBorrowerRowDto;
import com.plp.program.model.dto.EarlyPayCreateParameterRequest;
import com.plp.program.model.dto.EarlyPayCreateRequestDto;
import com.plp.program.model.dto.EarlyPayRepaymentUploadResult;
import com.plp.program.model.entity.Borrower;
import com.plp.program.model.entity.EarlyPayParameter;
import com.plp.program.model.entity.EarlyPayRepayment;
import com.plp.program.model.entity.EarlyPayRequest;
import com.plp.program.model.entity.Invoice;
import com.plp.program.model.entity.SubProgram;
import com.plp.program.model.entity.SubProgramBorrower;
import com.plp.program.model.enums.InvoiceDiscountingFlowType;
import com.plp.program.model.enums.InvoiceStatus;
import com.plp.program.repository.BorrowerRepository;
import com.plp.program.repository.EarlyPayParameterRepository;
import com.plp.program.repository.EarlyPayRepaymentRepository;
import com.plp.program.repository.EarlyPayRequestRepository;
import com.plp.program.repository.InvoiceRepository;
import com.plp.program.repository.SubProgramBorrowerRepository;
import com.plp.program.repository.SubProgramRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EarlyPayService {

    private static final String FLOW_SBD = InvoiceDiscountingFlowType.SALES_BILL_DISCOUNTING;
    private static final String YES = "YES";
    private static final String REQUESTED = "REQUESTED";
    private static final String APPROVED = "APPROVED";
    private static final String REJECTED = "REJECTED";

    private final EarlyPayParameterRepository parameterRepository;
    private final EarlyPayRequestRepository requestRepository;
    private final EarlyPayRepaymentRepository repaymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final SubProgramRepository subProgramRepository;
    private final SubProgramBorrowerRepository subProgramBorrowerRepository;
    private final BorrowerRepository borrowerRepository;
    private final EarlyPayEligibilityService eligibilityService;

    public boolean anchorHasEarlyPayEnabled(UUID anchorId) {
        return subProgramRepository.findByAnchorId(anchorId).stream()
                .anyMatch(sp -> FLOW_SBD.equals(sp.getFlowType()) && YES.equalsIgnoreCase(sp.getAllowEarlyPay()));
    }

    public List<SubProgram> listEarlyPaySubPrograms(UUID anchorId) {
        return subProgramRepository.findByAnchorId(anchorId).stream()
                .filter(sp -> FLOW_SBD.equals(sp.getFlowType()) && YES.equalsIgnoreCase(sp.getAllowEarlyPay()))
                .toList();
    }

    @Transactional
    public EarlyPayParameter createParameter(UUID anchorId, EarlyPayCreateParameterRequest req) {
        SubProgram sub = subProgramRepository.findById(req.getSubProgramId())
                .orElseThrow(() -> new IllegalArgumentException("Sub-program not found"));
        if (!anchorId.equals(sub.getAnchorId())) {
            throw new IllegalArgumentException("Sub-program does not belong to this anchor");
        }
        if (!FLOW_SBD.equals(sub.getFlowType())) {
            throw new IllegalArgumentException("Early Pay parameters only apply to SBD sub-programs");
        }
        if (!YES.equalsIgnoreCase(sub.getAllowEarlyPay())) {
            throw new IllegalArgumentException("Early Pay is not enabled on this sub-program");
        }
        LocalDate epDate = req.getEpDate() != null ? req.getEpDate() : LocalDate.now();
        if (parameterRepository.findBySubProgramIdAndEpDate(sub.getId(), epDate).isPresent()) {
            throw new IllegalArgumentException("Early Pay parameter already exists for date: " + epDate);
        }
        BigDecimal epAmount = req.getEpAmount();
        BigDecimal totalMargin = req.getTotalMarginAmount() != null ? req.getTotalMarginAmount() : BigDecimal.ZERO;
        EarlyPayParameter param = EarlyPayParameter.builder()
                .subProgramId(sub.getId())
                .anchorId(anchorId)
                .epDate(epDate)
                .discountPercentage(req.getDiscountPercentage())
                .epAmount(epAmount)
                .totalMarginAmount(totalMargin)
                .consumedMarginAmount(BigDecimal.ZERO)
                .unAllocatedAmount(epAmount != null ? epAmount : BigDecimal.ZERO)
                .status("ACTIVE")
                .build();
        return parameterRepository.save(param);
    }

    public List<EarlyPayParameter> listParameters(UUID anchorId, UUID subProgramId) {
        if (subProgramId != null) {
            return parameterRepository.findByAnchorIdAndSubProgramIdOrderByEpDateDesc(anchorId, subProgramId);
        }
        return parameterRepository.findByAnchorIdOrderByEpDateDesc(anchorId);
    }

    public List<EarlyPayBorrowerRowDto> listBorrowersForEnablement(UUID anchorId, UUID subProgramId) {
        SubProgram sub = subProgramRepository.findById(subProgramId)
                .orElseThrow(() -> new IllegalArgumentException("Sub-program not found"));
        if (!anchorId.equals(sub.getAnchorId()) || !FLOW_SBD.equals(sub.getFlowType())) {
            throw new IllegalArgumentException("Invalid SBD sub-program for anchor");
        }
        List<EarlyPayBorrowerRowDto> rows = new ArrayList<>();
        for (SubProgramBorrower membership : subProgramBorrowerRepository.findBySubProgramId(subProgramId)) {
            Borrower borrower = borrowerRepository.findById(membership.getBorrowerId()).orElse(null);
            rows.add(EarlyPayBorrowerRowDto.builder()
                    .membershipId(membership.getId())
                    .borrowerId(membership.getBorrowerId())
                    .borrowerName(borrower != null ? borrower.getName() : membership.getBorrowerId().toString())
                    .enableEarlyPay(membership.getEnableEarlyPay())
                    .build());
        }
        return rows;
    }

    @Transactional
    public SubProgramBorrower updateBorrowerEnablement(UUID anchorId, UUID membershipId, String enableEarlyPay) {
        SubProgramBorrower membership = subProgramBorrowerRepository.findById(membershipId)
                .orElseThrow(() -> new IllegalArgumentException("Borrower membership not found"));
        SubProgram sub = subProgramRepository.findById(membership.getSubProgramId())
                .orElseThrow(() -> new IllegalArgumentException("Sub-program not found"));
        if (!anchorId.equals(sub.getAnchorId())) {
            throw new IllegalArgumentException("Membership does not belong to this anchor");
        }
        membership.setEnableEarlyPay(YES.equalsIgnoreCase(enableEarlyPay) ? YES : "NO");
        return subProgramBorrowerRepository.save(membership);
    }

    @Transactional
    public EarlyPayRequest createRequest(UUID borrowerId, EarlyPayCreateRequestDto dto) {
        Invoice invoice = invoiceRepository.findById(dto.getInvoiceId())
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));
        if (!borrowerId.equals(invoice.getBorrowerId())) {
            throw new IllegalArgumentException("Invoice does not belong to this borrower");
        }
        if (!FLOW_SBD.equals(invoice.getFlowType())) {
            throw new IllegalArgumentException("Early Pay only applies to SBD invoices");
        }
        if (!InvoiceStatus.ELIGIBLE.name().equals(invoice.getStatus())) {
            throw new IllegalArgumentException("Early Pay request allowed only when invoice is ELIGIBLE");
        }
        if (!eligibilityService.isEarlyPayAllowed(invoice)) {
            throw new IllegalArgumentException("Early Pay is not enabled for this borrower/program");
        }
        requestRepository.findFirstByInvoiceIdAndStatusNot(invoice.getId(), REJECTED)
                .ifPresent(r -> {
                    throw new IllegalArgumentException("Early pay request already exists for this invoice");
                });

        EarlyPayParameter param = parameterRepository.findByIdForUpdate(dto.getEpParameterId())
                .orElseThrow(() -> new IllegalArgumentException("Early Pay parameter is not valid"));
        if (!invoice.getSubProgramId().equals(param.getSubProgramId())) {
            throw new IllegalArgumentException("Parameter does not match invoice sub-program");
        }

        BigDecimal balDue = EarlyPayEligibilityService.computeBalDueAmount(invoice);
        BigDecimal requestedAmount = dto.getRequestedAmount() != null
                ? dto.getRequestedAmount()
                : EarlyPayEligibilityService.computeRequestedAmount(balDue, param.getDiscountPercentage());
        BigDecimal available = EarlyPayEligibilityService.availableBlockedMargin(param);
        if (requestedAmount.compareTo(available) > 0) {
            throw new IllegalArgumentException("Requested amount exceeds anchor available Early Pay limit for today");
        }

        param.setConsumedMarginAmount(param.getConsumedMarginAmount().add(requestedAmount));
        parameterRepository.save(param);

        BigDecimal invoiceAmount = invoice.getInvoiceAmount() != null ? invoice.getInvoiceAmount() : balDue;
        EarlyPayRequest request = EarlyPayRequest.builder()
                .epParameterId(param.getId())
                .invoiceId(invoice.getId())
                .borrowerId(borrowerId)
                .subProgramId(invoice.getSubProgramId())
                .invoiceNo(invoice.getInvoiceNumber())
                .invoiceAmount(invoiceAmount)
                .requestedAmount(requestedAmount)
                .cdPercentage(param.getDiscountPercentage())
                .cdAmount(invoiceAmount.subtract(requestedAmount))
                .epDate(param.getEpDate())
                .status(REQUESTED)
                .build();
        EarlyPayRequest saved = requestRepository.save(request);

        invoice.setStatus(InvoiceStatus.DISCOUNTED_EP.name());
        invoiceRepository.save(invoice);
        return saved;
    }

    @Transactional
    public List<EarlyPayRequest> approveRequests(UUID anchorId, List<UUID> requestIds) {
        List<EarlyPayRequest> approved = new ArrayList<>();
        for (UUID requestId : requestIds) {
            try {
                approved.add(approveOne(anchorId, requestId));
            } catch (Exception e) {
                log.warn("Early Pay approve failed for {}: {}", requestId, e.getMessage());
            }
        }
        return approved;
    }

    private EarlyPayRequest approveOne(UUID anchorId, UUID requestId) {
        EarlyPayRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));
        if (!REQUESTED.equals(request.getStatus())) {
            throw new IllegalArgumentException("Request is not in REQUESTED status");
        }
        EarlyPayParameter param = parameterRepository.findByIdForUpdate(request.getEpParameterId())
                .orElseThrow(() -> new IllegalArgumentException("Parameter not found"));
        if (!anchorId.equals(param.getAnchorId())) {
            throw new IllegalArgumentException("Request does not belong to this anchor");
        }
        BigDecimal requested = request.getRequestedAmount();
        param.setConsumedMarginAmount(param.getConsumedMarginAmount().subtract(requested));
        param.setUnAllocatedAmount(param.getUnAllocatedAmount().subtract(requested));
        parameterRepository.save(param);

        request.setStatus(APPROVED);
        requestRepository.save(request);

        Invoice invoice = invoiceRepository.findById(request.getInvoiceId())
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));
        invoice.setStatus(InvoiceStatus.SANCTIONED_EP.name());
        invoiceRepository.save(invoice);
        return request;
    }

    @Transactional
    public List<EarlyPayRequest> rejectRequests(UUID anchorId, List<UUID> requestIds) {
        List<EarlyPayRequest> rejected = new ArrayList<>();
        for (UUID requestId : requestIds) {
            try {
                rejected.add(rejectOne(anchorId, requestId));
            } catch (Exception e) {
                log.warn("Early Pay reject failed for {}: {}", requestId, e.getMessage());
            }
        }
        return rejected;
    }

    private EarlyPayRequest rejectOne(UUID anchorId, UUID requestId) {
        EarlyPayRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));
        if (!REQUESTED.equals(request.getStatus())) {
            throw new IllegalArgumentException("Request is not in REQUESTED status");
        }
        EarlyPayParameter param = parameterRepository.findByIdForUpdate(request.getEpParameterId())
                .orElseThrow(() -> new IllegalArgumentException("Parameter not found"));
        if (!anchorId.equals(param.getAnchorId())) {
            throw new IllegalArgumentException("Request does not belong to this anchor");
        }
        param.setConsumedMarginAmount(param.getConsumedMarginAmount().subtract(request.getRequestedAmount()));
        parameterRepository.save(param);

        request.setStatus(REJECTED);
        requestRepository.save(request);

        Invoice invoice = invoiceRepository.findById(request.getInvoiceId())
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));
        invoice.setStatus(InvoiceStatus.ELIGIBLE.name());
        invoiceRepository.save(invoice);
        return request;
    }

    public List<EarlyPayRequest> listRequests(UUID anchorId, UUID subProgramId, String status) {
        List<UUID> subIds = listEarlyPaySubPrograms(anchorId).stream().map(SubProgram::getId).toList();
        if (subProgramId != null) {
            if (!subIds.contains(subProgramId)) {
                return List.of();
            }
            return requestRepository.findBySubProgramIdAndStatusOrderByCreatedAtDesc(subProgramId, status);
        }
        if (subIds.isEmpty()) {
            return List.of();
        }
        return requestRepository.findBySubProgramIdInAndStatusOrderByCreatedAtDesc(subIds, status);
    }

    public List<Invoice> listPaymentInvoices(UUID anchorId) {
        return invoiceRepository.findByAnchorId(anchorId).stream()
                .filter(inv -> InvoiceStatus.SANCTIONED_EP.name().equals(inv.getStatus()))
                .filter(inv -> FLOW_SBD.equals(inv.getFlowType()))
                .map(this::withEarlyPayPayout)
                .toList();
    }

    public List<Invoice> listClosedEpInvoices(UUID anchorId) {
        return invoiceRepository.findByAnchorId(anchorId).stream()
                .filter(inv -> InvoiceStatus.CLOSED.name().equals(inv.getStatus()))
                .filter(inv -> FLOW_SBD.equals(inv.getFlowType()))
                .filter(inv -> requestRepository.findFirstByInvoiceIdAndStatusNot(inv.getId(), REJECTED)
                        .map(r -> APPROVED.equals(r.getStatus()))
                        .orElse(false))
                .map(this::withEarlyPayPayout)
                .toList();
    }

    /** Attach the approved EP request payout amount (transient) for list display. */
    private Invoice withEarlyPayPayout(Invoice invoice) {
        requestRepository.findFirstByInvoiceIdAndStatus(invoice.getId(), APPROVED)
                .ifPresent(req -> invoice.setEarlyPayRequestedAmount(req.getRequestedAmount()));
        return invoice;
    }

    public List<EarlyPayRepayment> listRepayments(UUID anchorId) {
        return repaymentRepository.findByAnchorIdOrderByRepaidAtDesc(anchorId);
    }

    public List<EarlyPayRepayment> listRepaymentsForBorrower(UUID borrowerId) {
        return repaymentRepository.findByBorrowerIdOrderByRepaidAtDesc(borrowerId);
    }

    @Transactional
    public void rejectAllPendingRequests() {
        List<EarlyPayRequest> pending = requestRepository.findByStatus(REQUESTED);
        for (EarlyPayRequest request : pending) {
            try {
                EarlyPayParameter param = parameterRepository.findByIdForUpdate(request.getEpParameterId())
                        .orElse(null);
                if (param != null) {
                    param.setConsumedMarginAmount(
                            param.getConsumedMarginAmount().subtract(request.getRequestedAmount()));
                    parameterRepository.save(param);
                }
                request.setStatus(REJECTED);
                requestRepository.save(request);
                invoiceRepository.findById(request.getInvoiceId()).ifPresent(inv -> {
                    if (InvoiceStatus.DISCOUNTED_EP.name().equals(inv.getStatus())) {
                        inv.setStatus(InvoiceStatus.ELIGIBLE.name());
                        invoiceRepository.save(inv);
                    }
                });
            } catch (Exception e) {
                log.error("Nightly Early Pay reject failed for request {}: {}", request.getId(), e.getMessage());
            }
        }
    }

    @Transactional
    public EarlyPayRepaymentUploadResult uploadRepayments(UUID anchorId, MultipartFile file) {
        EarlyPayRepaymentUploadResult result = new EarlyPayRepaymentUploadResult();
        List<String> errors = new ArrayList<>();
        int processed = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null) {
                if (header) {
                    header = false;
                    continue;
                }
                if (line.isBlank()) {
                    continue;
                }
                String[] cols = line.split(",", -1);
                String invoiceNo = cols.length > 0 ? cols[0].trim() : "";
                if (invoiceNo.isEmpty()) {
                    errors.add("Invalid row (missing invoice no): " + line);
                    continue;
                }
                String borrowerCode = cols.length > 1 ? cols[1].trim() : "";
                Invoice invoice = findSanctionedEpInvoice(anchorId, invoiceNo, borrowerCode);
                if (invoice == null) {
                    errors.add("Invoice not found or not SANCTIONED_EP: " + invoiceNo);
                    continue;
                }
                EarlyPayRequest request = requestRepository
                        .findFirstByInvoiceIdAndStatus(invoice.getId(), APPROVED)
                        .orElse(null);
                if (request == null) {
                    errors.add("No approved Early Pay request for invoice: " + invoiceNo);
                    continue;
                }
                // Repayment amount is the Early Pay payout (requested amount), not the full invoice amount.
                BigDecimal repaymentAmount = request.getRequestedAmount();
                repaymentRepository.save(EarlyPayRepayment.builder()
                        .epRequestId(request.getId())
                        .invoiceId(invoice.getId())
                        .borrowerId(invoice.getBorrowerId())
                        .anchorId(anchorId)
                        .subProgramId(invoice.getSubProgramId())
                        .invoiceNo(invoice.getInvoiceNumber())
                        .amount(repaymentAmount)
                        .remarks("Repayment via anchor CSV upload")
                        .build());
                invoice.setStatus(InvoiceStatus.CLOSED.name());
                invoice.setClosedAt(Instant.now());
                invoiceRepository.save(invoice);
                processed++;
            }
        } catch (Exception e) {
            errors.add("File processing failed: " + e.getMessage());
        }
        result.setProcessedCount(processed);
        result.setErrors(errors);
        result.setRemarks(processed > 0 ? "Processed " + processed + " repayment(s)" : "No repayments processed");
        return result;
    }

    private Invoice findSanctionedEpInvoice(UUID anchorId, String invoiceNo, String borrowerCode) {
        return invoiceRepository.findByAnchorId(anchorId).stream()
                .filter(inv -> invoiceNo.equalsIgnoreCase(inv.getInvoiceNumber()))
                .filter(inv -> InvoiceStatus.SANCTIONED_EP.name().equals(inv.getStatus()))
                .filter(inv -> {
                    if (borrowerCode == null || borrowerCode.isBlank()) {
                        return true;
                    }
                    if (inv.getSubProgramId() == null) {
                        return false;
                    }
                    return subProgramBorrowerRepository
                            .findBySubProgramIdAndPartyCodeIgnoreCase(inv.getSubProgramId(), borrowerCode)
                            .map(m -> m.getBorrowerId().equals(inv.getBorrowerId()))
                            .orElse(false);
                })
                .findFirst()
                .orElse(null);
    }

    public EarlyPayParameter getTodayParameterForBorrower(UUID borrowerId, UUID subProgramId) {
        SubProgram sub = subProgramRepository.findById(subProgramId)
                .orElseThrow(() -> new IllegalArgumentException("Sub-program not found"));
        subProgramBorrowerRepository.findBySubProgramIdAndBorrowerId(subProgramId, borrowerId)
                .orElseThrow(() -> new IllegalArgumentException("Borrower not enrolled in sub-program"));
        return eligibilityService.findTodayParameter(subProgramId)
                .orElseThrow(() -> new IllegalArgumentException("No Early Pay parameter for today"));
    }
}
