package com.plp.program.service.earlypay;

import com.plp.program.model.entity.EarlyPayParameter;
import com.plp.program.model.entity.Invoice;
import com.plp.program.model.entity.SubProgram;
import com.plp.program.model.entity.SubProgramBorrower;
import com.plp.program.model.enums.InvoiceDiscountingFlowType;
import com.plp.program.repository.EarlyPayParameterRepository;
import com.plp.program.repository.SubProgramBorrowerRepository;
import com.plp.program.repository.SubProgramRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves SBD Early Pay gates for invoice list enrichment (legacy IMS showEarlyPay / isEarlyPayAllowed).
 */
@Service
@RequiredArgsConstructor
public class EarlyPayEligibilityService {

    private static final String FLOW_SBD = InvoiceDiscountingFlowType.SALES_BILL_DISCOUNTING;
    private static final String YES = "YES";
    private static final String NO = "NO";

    private final SubProgramRepository subProgramRepository;
    private final SubProgramBorrowerRepository subProgramBorrowerRepository;
    private final EarlyPayParameterRepository earlyPayParameterRepository;

    public void enrichInvoice(Invoice invoice) {
        if (invoice == null || !FLOW_SBD.equals(invoice.getFlowType())) {
            return;
        }
        invoice.setBalDueAmount(computeBalDueAmount(invoice));
        if (!isEarlyPayAllowed(invoice)) {
            invoice.setIsEarlyPayAllowed(NO);
            invoice.setShowEarlyPay(NO);
            return;
        }
        invoice.setIsEarlyPayAllowed(YES);
        invoice.setShowEarlyPay(hasParameterForToday(invoice.getSubProgramId()) ? YES : NO);
    }

    public boolean isEarlyPayAllowed(Invoice invoice) {
        if (invoice == null || !FLOW_SBD.equals(invoice.getFlowType())) {
            return false;
        }
        UUID subProgramId = invoice.getSubProgramId();
        UUID borrowerId = invoice.getBorrowerId();
        if (subProgramId == null || borrowerId == null) {
            return false;
        }
        Optional<SubProgram> subOpt = subProgramRepository.findById(subProgramId);
        if (subOpt.isEmpty() || !YES.equalsIgnoreCase(subOpt.get().getAllowEarlyPay())) {
            return false;
        }
        Optional<SubProgramBorrower> membership =
                subProgramBorrowerRepository.findBySubProgramIdAndBorrowerId(subProgramId, borrowerId);
        return membership.isPresent() && YES.equalsIgnoreCase(membership.get().getEnableEarlyPay());
    }

    public boolean hasParameterForToday(UUID subProgramId) {
        if (subProgramId == null) {
            return false;
        }
        return earlyPayParameterRepository
                .findBySubProgramIdAndEpDate(subProgramId, LocalDate.now())
                .isPresent();
    }

    public Optional<EarlyPayParameter> findTodayParameter(UUID subProgramId) {
        if (subProgramId == null) {
            return Optional.empty();
        }
        return earlyPayParameterRepository.findBySubProgramIdAndEpDate(subProgramId, LocalDate.now());
    }

    public static BigDecimal computeBalDueAmount(Invoice invoice) {
        BigDecimal net = invoice.getNetAmount() != null ? invoice.getNetAmount() : BigDecimal.ZERO;
        BigDecimal disc = invoice.getDiscountedAmount() != null ? invoice.getDiscountedAmount() : BigDecimal.ZERO;
        BigDecimal bal = net.subtract(disc);
        return bal.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : bal;
    }

    public static BigDecimal computeRequestedAmount(BigDecimal balDue, BigDecimal discountPercentage) {
        if (balDue == null || discountPercentage == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal pct = discountPercentage.divide(BigDecimal.valueOf(100), 8, java.math.RoundingMode.HALF_UP);
        BigDecimal discount = balDue.multiply(pct);
        return balDue.subtract(discount);
    }

    public static BigDecimal availableBlockedMargin(EarlyPayParameter param) {
        BigDecimal total = param.getTotalMarginAmount() != null ? param.getTotalMarginAmount() : BigDecimal.ZERO;
        BigDecimal consumed = param.getConsumedMarginAmount() != null ? param.getConsumedMarginAmount() : BigDecimal.ZERO;
        return total.subtract(consumed);
    }
}
