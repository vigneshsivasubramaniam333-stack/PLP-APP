package com.plp.lending.lms;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plp.lending.model.entity.Loan;
import com.plp.lending.model.enums.LoanStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class LmsSummaryAmountSyncTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void apply_usesEncoreSummaryForSanctionedAndPayoff() throws Exception {
        Loan loan = Loan.builder()
                .status(LoanStatus.SANCTIONED)
                .requestedAmount(new BigDecimal("2000"))
                .totalRepaid(BigDecimal.ZERO)
                .build();

        LmsSummaryAmountSync.apply(
                loan,
                objectMapper.readTree("""
                        {
                          "amountMagnitude": "1950.50",
                          "payOffAndDueAmount": "2100.75",
                          "totalNormalInterestDue": "150.25"
                        }
                        """));

        assertThat(loan.getSanctionedAmount()).isEqualByComparingTo("1950.50");
        assertThat(loan.getOutstandingAmount()).isEqualByComparingTo("2100.75");
        assertThat(loan.getTotalRepayable()).isEqualByComparingTo("2100.75");
        assertThat(loan.getInterestAmount()).isEqualByComparingTo("150.25");
    }
}
