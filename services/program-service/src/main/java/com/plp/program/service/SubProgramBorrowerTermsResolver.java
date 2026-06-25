package com.plp.program.service;

import com.plp.program.model.dto.EffectiveBorrowerTermsDto;
import com.plp.program.model.entity.Program;
import com.plp.program.model.entity.SubProgram;
import com.plp.program.model.entity.SubProgramBorrower;
import com.plp.program.model.enums.PaymentMethodMode;
import com.plp.program.repository.ProgramRepository;
import com.plp.program.repository.SubProgramBorrowerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubProgramBorrowerTermsResolver {

    private final SubProgramService subProgramService;
    private final SubProgramBorrowerRepository subProgramBorrowerRepository;
    private final ProgramRepository programRepository;
    private final ProductRepaymentDefaultService repaymentDefaultService;

    public EffectiveBorrowerTermsDto resolveEffectiveTerms(UUID subProgramId, UUID borrowerId) {
        SubProgram subProgram = subProgramService.getSubProgram(subProgramId);
        SubProgramBorrower membership = subProgramBorrowerRepository
                .findBySubProgramIdAndBorrowerId(subProgramId, borrowerId)
                .orElseThrow(() -> new RuntimeException("Borrower not enrolled in this sub program"));

        Program program = programRepository.findById(subProgram.getProgramId())
                .orElseThrow(() -> new RuntimeException("Program not found"));

        String paymentMethod = resolvePaymentMethod(membership, program);

        return EffectiveBorrowerTermsDto.builder()
                .subProgramId(subProgramId)
                .borrowerId(borrowerId)
                .programId(subProgram.getProgramId())
                .interestRate(firstNonNull(membership.getInterestRate(), subProgram.getInterestRate()))
                .discountMarginPercent(firstNonNull(membership.getDiscountMarginPercent(), subProgram.getMarginPercent()))
                .creditPeriodDays(firstNonNull(membership.getCreditPeriodDays(), subProgram.getMaxTenureDays()))
                .discountHold(membership.getDiscountHold() != null ? membership.getDiscountHold() : "NO")
                .paymentMethod(paymentMethod)
                .paymentMethodMode(membership.getPaymentMethodMode() != null
                        ? membership.getPaymentMethodMode().name()
                        : PaymentMethodMode.CUSTOM.name())
                .overdueInterestRate(membership.getOverdueInterestRate())
                .borrowerLimit(membership.getBorrowerLimit())
                .subProgramInterestRate(subProgram.getInterestRate())
                .subProgramMaxTenureDays(subProgram.getMaxTenureDays())
                .subProgramMarginPercent(subProgram.getMarginPercent())
                .build();
    }

    private String resolvePaymentMethod(SubProgramBorrower membership, Program program) {
        if (membership.getPaymentMethodMode() == PaymentMethodMode.GLOBAL) {
            return repaymentDefaultService.resolveMechanism(program.getProductType());
        }
        return membership.getPaymentMethod() != null ? membership.getPaymentMethod() : "SMART_COLLECT";
    }

    private static <T> T firstNonNull(T primary, T fallback) {
        return primary != null ? primary : fallback;
    }
}
