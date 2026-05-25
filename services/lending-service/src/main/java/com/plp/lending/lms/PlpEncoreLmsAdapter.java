package com.plp.lending.lms;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.plp.encore.client.api.EncoreOpenLoanParams;
import com.plp.encore.client.api.EncoreTemporaryOverrides;
import com.plp.encore.client.config.EncoreClientProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static com.plp.encore.client.api.EncoreTemporaryOverrides.DEFAULT_ENCORE_LOCATION_CODE;
import static com.plp.encore.client.api.EncoreTemporaryOverrides.buildEncoreCustomerId;

/**
 * Builds Encore {@code loanOdAccount} JSON aligned with bl-core / LOS {@code BlCoreEncoreLmsAdapter}.
 */
@Component
@RequiredArgsConstructor
public class PlpEncoreLmsAdapter {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final EncoreClientProperties properties;
    private final ObjectMapper objectMapper;

    public ObjectNode buildLoanOdAccount(EncoreOpenLoanParams p, Map<String, Object> borrowerDetails) {
        ObjectNode acc = objectMapper.createObjectNode();

        String disbDateStr = p.disbursementDate() != null ? p.disbursementDate()
                : LocalDate.now().format(DATE_FORMAT);
        LocalDate disbDate = LocalDate.parse(disbDateStr, DATE_FORMAT);
        LocalDate disbByDate = disbDate.plusMonths(1);

        acc.putNull("accountId");
        acc.put("openedOnDate", disbDateStr);
        acc.put("disbursementByDate", disbByDate.format(DATE_FORMAT));
        acc.putNull("firstRepaymentDate");
        acc.put("amountMagnitude", p.sanctionedAmount().toPlainString());
        acc.put("branchCode", coalesce(p.branchCode(), properties.getAdminBranch()));
        acc.put("currencyCode", properties.getCurrency());
        acc.put("customer1FirstName", p.borrowerName() != null ? p.borrowerName() : "");
        acc.put("customer1MiddleName", "");
        acc.put("customer1LastName", "");

        String rawCustomerId = coalesce(p.customerId(), p.applicationNumber());
        acc.put("customerId1", buildEncoreCustomerId(rawCustomerId));

        acc.put("migrated", "false");
        acc.put("normalInterestRate", p.interestRate() != null ? p.interestRate().toPlainString() : "0");
        acc.put("operationalStatus", "active");
        BigDecimal penalRate = p.penalInterestRate() != null ? p.penalInterestRate() : BigDecimal.ZERO;
        acc.put("penalInterestRate", penalRate.toPlainString());

        String productCode = p.productCode() != null && !p.productCode().isBlank()
                ? p.productCode()
                : EncoreTemporaryOverrides.DEFAULT_ENCORE_PRODUCT_CODE;
        acc.put("productCode", productCode);
        acc.put("productType", properties.getLoanProductType());

        int tenure = p.numberOfInstallments() > 0 ? p.numberOfInstallments() : p.tenureMonths();
        acc.put("tenureMagnitude", String.valueOf(tenure));
        acc.put("tenureUnit", coalesce(p.tenureUnit(), "Month"));
        acc.put("securityDepositAllowed", true);
        acc.put("moratoriumType", coalesce(p.moratoriumType(), "None"));
        acc.put("moratoriumPeriodMagnitude", String.valueOf(p.moratoriumPeriodMagnitude()));
        acc.put("moratoriumPeriodUnit", coalesce(p.moratoriumPeriodUnit(), "Month"));
        acc.put("moratoriumNormalInterestRateApplicable", false);
        acc.putNull("moratoriumNormalInterestRate");
        acc.putNull("moratoriumInterestAccrualCalculation");

        putIfPresent(acc, "customer1PinCode", geoFrom(borrowerDetails, "pinCode", "pincode", "postalCode"));
        acc.put("customer1CityCode", DEFAULT_ENCORE_LOCATION_CODE);
        acc.put("customer1CountryCode", DEFAULT_ENCORE_LOCATION_CODE);
        acc.put("customer1StateCode", DEFAULT_ENCORE_LOCATION_CODE);
        acc.put("colendingApplicable", "false");
        return acc;
    }

    private static void putIfPresent(ObjectNode node, String field, String value) {
        if (value != null && !value.isBlank()) {
            node.put(field, value);
        }
    }

    private static String geoFrom(Map<String, Object> d, String... keys) {
        if (d == null) {
            return null;
        }
        for (String k : keys) {
            Object v = d.get(k);
            if (v == null) {
                continue;
            }
            String s = String.valueOf(v).trim();
            if (!s.isEmpty()) {
                return s;
            }
        }
        return null;
    }

    private static String coalesce(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
