package com.plp.program.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProgramFieldDefinitionServiceTranslateTest {

    @Test
    void translateLosCustomFields_renamesSystemKeys() {
        Map<String, Object> los = new LinkedHashMap<>();
        los.put("maxInvoiceVintageDays", 45);
        los.put("tenureDays", 90);
        los.put("maxCmr", 6);
        los.put("customNote", "hello");

        Map<String, Object> plp = ProgramFieldDefinitionService.translateLosCustomFields(los);

        assertThat(plp)
                .containsEntry("maxInvoiceAgeDays", 45)
                .containsEntry("maxTenureDays", 90)
                .containsEntry("maxCmr", 6)
                .containsEntry("customNote", "hello")
                .doesNotContainKey("maxInvoiceVintageDays")
                .doesNotContainKey("tenureDays");
    }

    @Test
    void translateLosCustomFields_skipsBlanks() {
        Map<String, Object> los = Map.of(
                "maxCmr", "",
                "minCibil", 700);
        Map<String, Object> plp = ProgramFieldDefinitionService.translateLosCustomFields(los);
        assertThat(plp).containsOnlyKeys("minCibil");
    }
}
