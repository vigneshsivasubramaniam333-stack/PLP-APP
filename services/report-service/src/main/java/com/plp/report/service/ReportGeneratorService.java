package com.plp.report.service;

import com.plp.report.model.dto.DisbursementSummary;
import com.plp.report.model.dto.OverdueReport;
import com.plp.report.model.dto.PortfolioSummary;
import com.plp.report.model.entity.GeneratedReport;
import com.plp.report.model.entity.ReportDefinition;
import com.plp.report.model.enums.ReportStatus;
import com.plp.report.repository.GeneratedReportRepository;
import com.plp.report.repository.ReportDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportGeneratorService {

    private final RestTemplate restTemplate;
    private final GeneratedReportRepository generatedReportRepository;
    private final ReportDefinitionRepository reportDefinitionRepository;

    public List<DisbursementSummary> generateDisbursementSummary(LocalDate fromDate, LocalDate toDate) {
        try {
            List<Map<String, Object>> loans = fetchAllLoans();
            Map<String, DisbursementSummary> grouped = new LinkedHashMap<>();

            for (Map<String, Object> loan : loans) {
                String status = (String) loan.get("status");
                if (!"DISBURSED".equals(status) && !"REPAYMENT_DUE".equals(status) &&
                    !"OVERDUE".equals(status) && !"CLOSED".equals(status)) continue;

                String productType = (String) loan.get("productType");
                String sanctionDate = loan.get("sanctionDate") != null ? loan.get("sanctionDate").toString() : "";
                if (sanctionDate.isEmpty()) continue;

                LocalDate loanDate = LocalDate.parse(sanctionDate);
                if (fromDate != null && loanDate.isBefore(fromDate)) continue;
                if (toDate != null && loanDate.isAfter(toDate)) continue;

                String key = sanctionDate + "|" + productType;
                DisbursementSummary summary = grouped.computeIfAbsent(key, k ->
                        DisbursementSummary.builder()
                                .date(sanctionDate)
                                .productType(productType)
                                .loanCount(0)
                                .totalDisbursed(BigDecimal.ZERO)
                                .totalApproved(BigDecimal.ZERO)
                                .build());

                summary.setLoanCount(summary.getLoanCount() + 1);
                BigDecimal disbursed = toBigDecimal(loan.get("disbursedAmount"));
                BigDecimal sanctioned = toBigDecimal(loan.get("sanctionedAmount"));
                summary.setTotalDisbursed(summary.getTotalDisbursed().add(disbursed));
                summary.setTotalApproved(summary.getTotalApproved().add(sanctioned));
            }
            return new ArrayList<>(grouped.values());
        } catch (Exception e) {
            log.error("Failed to generate disbursement summary: {}", e.getMessage());
            return List.of();
        }
    }

    public List<PortfolioSummary> generatePortfolioSummary() {
        try {
            List<Map<String, Object>> loans = fetchAllLoans();
            List<Map<String, Object>> programs = fetchAllPrograms();

            Map<String, Map<String, Object>> programMap = new HashMap<>();
            for (Map<String, Object> p : programs) {
                programMap.put(p.get("id").toString(), p);
            }

            Map<String, PortfolioSummary> grouped = new LinkedHashMap<>();

            for (Map<String, Object> loan : loans) {
                String programId = loan.get("programId") != null ? loan.get("programId").toString() : "UNKNOWN";
                Map<String, Object> program = programMap.getOrDefault(programId, Map.of());
                String programCode = (String) program.getOrDefault("programCode", "N/A");
                String programName = (String) program.getOrDefault("programName", "N/A");
                String productType = (String) loan.getOrDefault("productType", "N/A");
                String status = (String) loan.get("status");

                PortfolioSummary summary = grouped.computeIfAbsent(programId, k ->
                        PortfolioSummary.builder()
                                .programCode(programCode)
                                .programName(programName)
                                .productType(productType)
                                .totalLoans(0).activeLoans(0).overdueLoans(0)
                                .totalDisbursed(BigDecimal.ZERO)
                                .totalOutstanding(BigDecimal.ZERO)
                                .totalOverdue(BigDecimal.ZERO)
                                .npaPercent(0.0)
                                .build());

                summary.setTotalLoans(summary.getTotalLoans() + 1);
                summary.setTotalDisbursed(summary.getTotalDisbursed().add(toBigDecimal(loan.get("disbursedAmount"))));

                if ("DISBURSED".equals(status) || "REPAYMENT_DUE".equals(status) || "OVERDUE".equals(status)) {
                    summary.setActiveLoans(summary.getActiveLoans() + 1);
                    summary.setTotalOutstanding(summary.getTotalOutstanding().add(toBigDecimal(loan.get("outstandingAmount"))));
                }
                if ("OVERDUE".equals(status)) {
                    summary.setOverdueLoans(summary.getOverdueLoans() + 1);
                    summary.setTotalOverdue(summary.getTotalOverdue().add(toBigDecimal(loan.get("outstandingAmount"))));
                }
            }

            for (PortfolioSummary s : grouped.values()) {
                if (s.getTotalDisbursed().compareTo(BigDecimal.ZERO) > 0) {
                    s.setNpaPercent(s.getTotalOverdue()
                            .multiply(BigDecimal.valueOf(100))
                            .divide(s.getTotalDisbursed(), 2, RoundingMode.HALF_UP)
                            .doubleValue());
                }
            }
            return new ArrayList<>(grouped.values());
        } catch (Exception e) {
            log.error("Failed to generate portfolio summary: {}", e.getMessage());
            return List.of();
        }
    }

    public List<OverdueReport> generateOverdueReport() {
        try {
            List<Map<String, Object>> loans = fetchAllLoans();
            List<Map<String, Object>> programs = fetchAllPrograms();
            Map<String, Map<String, Object>> programMap = programs.stream()
                    .collect(Collectors.toMap(p -> p.get("id").toString(), p -> p, (a, b) -> a));

            return loans.stream()
                    .filter(l -> "OVERDUE".equals(l.get("status")) || "REPAYMENT_DUE".equals(l.get("status")))
                    .map(loan -> {
                        String programId = loan.get("programId") != null ? loan.get("programId").toString() : "";
                        Map<String, Object> program = programMap.getOrDefault(programId, Map.of());
                        int dpd = loan.get("dpd") != null ? ((Number) loan.get("dpd")).intValue() : 0;
                        return OverdueReport.builder()
                                .loanNumber((String) loan.get("loanNumber"))
                                .borrowerName(loan.get("borrowerId") != null ? loan.get("borrowerId").toString() : "")
                                .programCode((String) program.getOrDefault("programCode", "N/A"))
                                .productType((String) loan.getOrDefault("productType", "N/A"))
                                .outstandingAmount(toBigDecimal(loan.get("outstandingAmount")))
                                .dpd(dpd)
                                .dpdBucket(getDpdBucket(dpd))
                                .dueDate(loan.get("dueDate") != null ? loan.get("dueDate").toString() : "")
                                .build();
                    })
                    .sorted(Comparator.comparingInt(OverdueReport::getDpd).reversed())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to generate overdue report: {}", e.getMessage());
            return List.of();
        }
    }

    public Map<String, Object> generateDashboardStats() {
        try {
            List<Map<String, Object>> loans = fetchAllLoans();
            List<Map<String, Object>> programs = fetchAllPrograms();

            long activeLoans = loans.stream()
                    .filter(l -> Set.of("DISBURSED", "REPAYMENT_DUE", "OVERDUE").contains(l.get("status")))
                    .count();
            long overdueLoans = loans.stream()
                    .filter(l -> "OVERDUE".equals(l.get("status")))
                    .count();
            BigDecimal totalDisbursed = loans.stream()
                    .map(l -> toBigDecimal(l.get("disbursedAmount")))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalOutstanding = loans.stream()
                    .filter(l -> Set.of("DISBURSED", "REPAYMENT_DUE", "OVERDUE").contains(l.get("status")))
                    .map(l -> toBigDecimal(l.get("outstandingAmount")))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Map<String, Long> loansByProduct = loans.stream()
                    .collect(Collectors.groupingBy(
                            l -> (String) l.getOrDefault("productType", "UNKNOWN"),
                            Collectors.counting()));
            Map<String, Long> loansByStatus = loans.stream()
                    .collect(Collectors.groupingBy(
                            l -> (String) l.getOrDefault("status", "UNKNOWN"),
                            Collectors.counting()));

            return Map.of(
                    "totalLoans", loans.size(),
                    "activeLoans", activeLoans,
                    "overdueLoans", overdueLoans,
                    "totalPrograms", programs.size(),
                    "totalDisbursed", totalDisbursed,
                    "totalOutstanding", totalOutstanding,
                    "loansByProduct", loansByProduct,
                    "loansByStatus", loansByStatus
            );
        } catch (Exception e) {
            log.error("Failed to generate dashboard stats: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    @Transactional
    public GeneratedReport createReportRecord(String reportCode, UUID requestedBy, String params) {
        ReportDefinition def = reportDefinitionRepository.findByReportCode(reportCode).orElse(null);
        GeneratedReport report = GeneratedReport.builder()
                .reportDefinition(def)
                .requestedBy(requestedBy)
                .parametersUsed(params)
                .status(ReportStatus.COMPLETED)
                .generatedAt(OffsetDateTime.now())
                .expiresAt(OffsetDateTime.now().plusDays(30))
                .build();
        return generatedReportRepository.save(report);
    }

    public Page<GeneratedReport> getReports(UUID requestedBy, Pageable pageable) {
        return generatedReportRepository.findByRequestedByOrderByCreatedAtDesc(requestedBy, pageable);
    }

    public List<ReportDefinition> getReportDefinitions() {
        return reportDefinitionRepository.findAll();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchAllLoans() {
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    "http://lending-service/api/v1/loans",
                    HttpMethod.GET, null,
                    new ParameterizedTypeReference<>() {});
            Map<String, Object> body = response.getBody();
            if (body != null && body.containsKey("data")) {
                Object data = body.get("data");
                if (data instanceof List) return (List<Map<String, Object>>) data;
            }
            return List.of();
        } catch (Exception e) {
            log.error("Failed to fetch loans: {}", e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchAllPrograms() {
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    "http://program-service/api/v1/programs",
                    HttpMethod.GET, null,
                    new ParameterizedTypeReference<>() {});
            Map<String, Object> body = response.getBody();
            if (body != null && body.containsKey("data")) {
                Object data = body.get("data");
                if (data instanceof List) return (List<Map<String, Object>>) data;
            }
            return List.of();
        } catch (Exception e) {
            log.error("Failed to fetch programs: {}", e.getMessage());
            return List.of();
        }
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return BigDecimal.valueOf(((Number) value).doubleValue());
        try { return new BigDecimal(value.toString()); } catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }

    private String getDpdBucket(int dpd) {
        if (dpd <= 0) return "CURRENT";
        if (dpd <= 30) return "1-30";
        if (dpd <= 60) return "31-60";
        if (dpd <= 90) return "61-90";
        return "90+";
    }
}
