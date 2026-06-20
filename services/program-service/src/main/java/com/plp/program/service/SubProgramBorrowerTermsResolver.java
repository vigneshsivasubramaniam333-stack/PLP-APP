package com.plp.program.service;

import com.plp.program.model.dto.EffectiveBorrowerTermsDto;
import com.plp.program.model.entity.SubProgram;
import com.plp.program.model.entity.SubProgramBorrower;
import com.plp.program.repository.SubProgramBorrowerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubProgramBorrowerTermsResolver {

    private final SubProgramService subProgramService;
    private final SubProgramBorrowerRepository subProgramBorrowerRepository;

    public EffectiveBorrowerTermsDto resolveEffectiveTerms(UUID subProgramId, UUID borrowerId) {
        SubProgram subProgram = subProgramService.getSubProgram(subProgramId);
        SubProgramBorrower membership = subProgramBorrowerRepository
                .findBySubProgramIdAndBorrowerId(subProgramId, borrowerId)
                .orElseThrow(() -> new RuntimeException("Borrower not enrolled in this sub program"));

        return EffectiveBorrowerTermsDto.builder()
                .subProgramId(subProgramId)
                .borrowerId(borrowerId)
                .programId(subProgram.getProgramId())
                .interestRate(firstNonNull(membership.getInterestRate(), subProgram.getInterestRate()))
                .discountMarginPercent(firstNonNull(membership.getDiscountMarginPercent(), subProgram.getMarginPercent()))
                .creditPeriodDays(firstNonNull(membership.getCreditPeriodDays(), subProgram.getMaxTenureDays()))
                .discountHold(membership.getDiscountHold() != null ? membership.getDiscountHold() : "NO")
                .paymentMethod(membership.getPaymentMethod() != null ? membership.getPaymentMethod() : "SMART_COLLECT")
                .overdueInterestRate(membership.getOverdueInterestRate())
                .borrowerLimit(membership.getBorrowerLimit())
                .subProgramInterestRate(subProgram.getInterestRate())
                .subProgramMaxTenureDays(subProgram.getMaxTenureDays())
                .subProgramMarginPercent(subProgram.getMarginPercent())
                .build();
    }

    private static <T> T firstNonNull(T primary, T fallback) {
        return primary != null ? primary : fallback;
    }
}
