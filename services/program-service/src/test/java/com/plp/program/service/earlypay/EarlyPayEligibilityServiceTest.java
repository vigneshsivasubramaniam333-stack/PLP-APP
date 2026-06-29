package com.plp.program.service.earlypay;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class EarlyPayEligibilityServiceTest {

    @Test
    void computeRequestedAmount_appliesDiscountPercentage() {
        BigDecimal balDue = new BigDecimal("1000");
        BigDecimal pct = new BigDecimal("5");
        BigDecimal requested = EarlyPayEligibilityService.computeRequestedAmount(balDue, pct);
        assertThat(requested).isEqualByComparingTo("950");
    }

    @Test
    void availableBlockedMargin_subtractsConsumedFromTotal() {
        var param = com.plp.program.model.entity.EarlyPayParameter.builder()
                .totalMarginAmount(new BigDecimal("50000"))
                .consumedMarginAmount(new BigDecimal("12000"))
                .build();
        assertThat(EarlyPayEligibilityService.availableBlockedMargin(param))
                .isEqualByComparingTo("38000");
    }
}
