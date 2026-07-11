package com.plp.program.validation;

import com.plp.program.model.enums.ProductType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProgramParametersValidatorTest {

    @Test
    void defaultParameters_containsAllKeys() {
        Map<String, Object> defaults = ProgramParametersValidator.defaultParameters();
        assertThat(defaults).containsKeys(
                "gapBetweenPreviousInvoiceDays",
                "autoPullOption",
                "sanctionType",
                "partialDiscount",
                "invoiceDelete");
        assertThat(defaults.get("discountingDay")).isEqualTo(0);
        assertThat(defaults.get("sanctionType")).isEqualTo("MANUAL");
    }

    @Test
    void intFreePeriodRequiredWhenFlagEnabled() {
        Map<String, Object> params = new HashMap<>(ProgramParametersValidator.defaultParameters());
        params.put("intFreeCreditPeriod", true);
        params.put("intFreePeriodDays", 0);
        assertThatThrownBy(() -> ProgramParametersValidator.validateAndNormalize(params, ProductType.INVOICE_DISCOUNTING))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("intFreePeriodDays");
    }

    @Test
    void maxInvoiceAgeDaysDefaultsTo90ForInvoiceDiscounting() {
        Map<String, Object> config = ProgramParametersValidator.defaultConfig(ProductType.INVOICE_DISCOUNTING);
        assertThat(config.get("maxInvoiceAgeDays")).isEqualTo(90);
    }
}
