package com.plp.program.validation;

import com.plp.program.model.entity.SubProgram;
import com.plp.program.model.entity.SubProgramBorrower;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubProgramBorrowerTermsValidatorTest {

    @Test
    void appliesDefaultsWhenBlank() {
        SubProgram sp = SubProgram.builder().interestRate(new BigDecimal("12")).maxTenureDays(90).build();
        SubProgramBorrower membership = SubProgramBorrower.builder().build();
        SubProgramBorrowerTermsValidator.validateAndApplyDefaults(membership, sp);
        assertThat(membership.getDiscountHold()).isEqualTo("NO");
        assertThat(membership.getPaymentMethod()).isEqualTo("SMART_COLLECT");
        assertThat(membership.getOverdueInterestRate()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void rejectsInterestAboveSubProgramCap() {
        SubProgram sp = SubProgram.builder().interestRate(new BigDecimal("10")).build();
        SubProgramBorrower membership = SubProgramBorrower.builder().interestRate(new BigDecimal("11")).build();
        assertThatThrownBy(() -> SubProgramBorrowerTermsValidator.validateAndApplyDefaults(membership, sp))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("interestRate");
    }
}
