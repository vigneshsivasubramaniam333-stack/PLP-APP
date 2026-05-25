package com.plp.lending.service;

import com.plp.lending.integration.ProgramServiceInvoiceSubProgramValidator;
import com.plp.lending.integration.ProgramServiceProgramConfigClient;
import com.plp.lending.integration.ProgramServiceSalarySlipClient;
import com.plp.lending.integration.ProgramServiceSubProgramLimits;
import com.plp.lending.model.enums.LoanStatus;
import com.plp.lending.repository.LoanRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EligibilityServicePayDaySalaryReuseTest {

    @Mock
    LoanRepository loanRepository;

    @Mock
    RestTemplate restTemplate;

    @Mock
    ProgramServiceInvoiceSubProgramValidator invoiceSubProgramValidator;

    @Mock
    ProgramServiceSubProgramLimits subProgramLimits;

    @Mock
    ProgramServiceProgramConfigClient programServiceProgramConfigClient;

    @Mock
    ProgramServiceSalarySlipClient salarySlipClient;

    @InjectMocks
    EligibilityService eligibilityService;

    @Captor
    ArgumentCaptor<Collection<LoanStatus>> statusCaptor;

    @Test
    void checkPayDayLoanEligibility_blocksWhenSalaryLinkedLoanExists_statusSetIncludesClosed() {
        UUID borrowerId = UUID.randomUUID();
        UUID programId = UUID.randomUUID();
        UUID salaryId = UUID.randomUUID();
        UUID subProgramId = UUID.randomUUID();

        when(loanRepository.findByBorrowerId(borrowerId)).thenReturn(List.of());

        Map<String, Object> limitsBody = new LinkedHashMap<>();
        limitsBody.put("status", "SUCCESS");
        limitsBody.put("data", Map.of("maxConcurrentLoans", 5));
        when(restTemplate.exchange(
                        eq("http://program-service/api/v1/borrowers/{borrowerId}/limits?programId={programId}"),
                        eq(HttpMethod.GET),
                        nullable(HttpEntity.class),
                        eq(Map.class),
                        eq(borrowerId),
                        eq(programId)))
                .thenReturn(ResponseEntity.ok(limitsBody));

        Map<String, Object> spRow = new LinkedHashMap<>();
        spRow.put("id", subProgramId.toString());
        spRow.put("status", "ACTIVE");
        spRow.put("flowType", "PAY_LOAN");
        spRow.put("interestRate", 12);
        Map<String, Object> subProgramsBody = new LinkedHashMap<>();
        subProgramsBody.put("status", "SUCCESS");
        subProgramsBody.put("data", List.of(spRow));
        when(restTemplate.exchange(
                        eq("http://program-service/api/v1/programs/{programId}/sub-programs"),
                        eq(HttpMethod.GET),
                        nullable(HttpEntity.class),
                        eq(Map.class),
                        eq(programId)))
                .thenReturn(ResponseEntity.ok(subProgramsBody));

        Map<String, Object> salaryData = new LinkedHashMap<>();
        salaryData.put("id", salaryId.toString());
        salaryData.put("programId", programId.toString());
        salaryData.put("eligibleAmount", 100_000);
        salaryData.put("slipStatus", "AVAILABLE_FOR_LOAN");
        salaryData.put("updatedAt", "2020-01-01T00:00:00Z");
        salaryData.put("createdAt", "2020-01-01T00:00:00Z");

        Map<String, Object> salaryListBody = new LinkedHashMap<>();
        salaryListBody.put("status", "SUCCESS");
        salaryListBody.put("data", List.of(salaryData));
        when(restTemplate.exchange(
                        eq("http://program-service/api/v1/salary?borrowerId={borrowerId}"),
                        eq(HttpMethod.GET),
                        nullable(HttpEntity.class),
                        eq(Map.class),
                        eq(borrowerId)))
                .thenReturn(ResponseEntity.ok(salaryListBody));

        BigDecimal headroom = new BigDecimal("500000");
        ProgramServiceSubProgramLimits.DualSubProgramBorrowerAvailability dual =
                new ProgramServiceSubProgramLimits.DualSubProgramBorrowerAvailability(headroom, headroom, headroom);
        when(subProgramLimits.fetchDualLimitHeadroom(eq(subProgramId), eq(borrowerId))).thenReturn(Optional.of(dual));

        when(loanRepository.existsBySalaryDataIdAndStatusIn(eq(salaryId), statusCaptor.capture()))
                .thenReturn(true);

        Map<String, Object> result =
                eligibilityService.checkPayDayLoanEligibility(borrowerId, programId, new BigDecimal("10000"));

        assertThat(result.get("eligible")).isEqualTo(false);
        @SuppressWarnings("unchecked")
        List<String> reasons = (List<String>) result.get("reasons");
        assertThat(reasons).contains("Loan already exists for this salary period.");

        Collection<LoanStatus> passed = statusCaptor.getValue();
        assertThat(passed).contains(LoanStatus.CLOSED, LoanStatus.REQUESTED, LoanStatus.ELIGIBILITY_CHECK);
        assertThat(passed).doesNotContain(LoanStatus.REJECTED, LoanStatus.CANCELLED, LoanStatus.WRITTEN_OFF);
    }
}
