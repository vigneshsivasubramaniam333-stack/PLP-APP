package com.plp.program.service;

import com.plp.program.integration.LendingServiceFinanceClient;
import com.plp.program.model.entity.Invoice;
import com.plp.program.model.enums.InvoiceDiscountingFlowType;
import com.plp.program.model.entity.Program;
import com.plp.program.model.enums.InvoiceStatus;
import com.plp.program.repository.InvoiceRepository;
import com.plp.program.validation.ProgramParametersValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Shared auto-finance (auto-pull / auto-discounting) logic for eligible invoices.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceAutoFinanceService {

    private static final String FLOW_PURCHASE_BILL_DISCOUNTING = InvoiceDiscountingFlowType.PURCHASE_BILL_DISCOUNTING;

    private final InvoiceRepository invoiceRepository;
    private final ProgramService programService;
    private final LendingServiceFinanceClient lendingServiceFinanceClient;

    public void maybeAutoPullFinance(Invoice invoice) {
        Map<String, Object> params = programService.getProgramParameters(invoice.getProgramId());
        if (!ProgramParametersValidator.parseYesNo(params.get("autoPullOption"), false)) {
            return;
        }
        attemptFinance(invoice, params);
    }

    public int runAutoDiscountingForProgram(Program program, LocalDate today) {
        Map<String, Object> params = program.getParameters();
        if (params == null) {
            return 0;
        }
        try {
            params = ProgramParametersValidator.validateAndNormalize(params, program.getProductType());
        } catch (IllegalArgumentException e) {
            return 0;
        }
        if (!ProgramParametersValidator.parseYesNo(params.get("autoDiscounting"), false)) {
            return 0;
        }
        int discountingDay = params.get("discountingDay") instanceof Number n ? n.intValue() : 0;
        if (discountingDay != today.getDayOfMonth()) {
            return 0;
        }
        int gapDays = params.get("gapBetweenDiscountingDays") instanceof Number n ? n.intValue() : 0;
        int processed = 0;
        List<Invoice> invoices = invoiceRepository.findByProgramId(program.getId());
        for (Invoice invoice : invoices) {
            if (!isReadyForAutoFinance(invoice)) {
                continue;
            }
            if (!gapAllowsFinance(invoice, gapDays, today)) {
                continue;
            }
            if (attemptFinance(invoice, params)) {
                processed++;
            }
        }
        return processed;
    }

    private boolean isReadyForAutoFinance(Invoice invoice) {
        if (InvoiceStatus.FINANCING_REQUESTED.name().equals(invoice.getStatus())) {
            return false;
        }
        String status = invoice.getStatus();
        boolean purchaseReady = "BORROWER_ACCEPTED".equals(status) || "PARTIALLY_DISCOUNTED".equals(status);
        boolean salesReady = "ELIGIBLE".equals(status) || "PARTIALLY_DISCOUNTED".equals(status);
        String flow = invoice.getFlowType();
        boolean purchaseFlow = InvoiceDiscountingFlowType.isPurchaseBill(flow);
        return purchaseFlow ? purchaseReady : salesReady;
    }

    private boolean gapAllowsFinance(Invoice invoice, int gapDays, LocalDate today) {
        if (gapDays <= 0) {
            return true;
        }
        Instant last = invoice.getLastFinanceRequestedAt();
        if (last == null) {
            return true;
        }
        long days = ChronoUnit.DAYS.between(last.atZone(java.time.ZoneId.systemDefault()).toLocalDate(), today);
        return days >= gapDays;
    }

    private boolean attemptFinance(Invoice invoice, Map<String, Object> params) {
        if (!isReadyForAutoFinance(invoice)) {
            return false;
        }
        BigDecimal amount = invoice.getAvailableAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            amount = invoice.getEligibleAmount();
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Auto-finance skipped for invoice {}: no financeable amount", invoice.getId());
            return false;
        }
        boolean ok = lendingServiceFinanceClient.requestInvoiceFinance(
                invoice.getId(), invoice.getBorrowerId(), invoice.getProgramId(), amount);
        if (ok) {
            log.info("Auto-finance requested for invoice {} amount {}", invoice.getId(), amount);
        }
        return ok;
    }
}
