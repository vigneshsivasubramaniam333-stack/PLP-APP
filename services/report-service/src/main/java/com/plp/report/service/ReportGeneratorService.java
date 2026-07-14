package com.plp.report.service;

import com.plp.report.config.ReportServiceInternalHeaders;
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
import org.springframework.http.HttpEntity;
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

    /**
     * Daily collection/repayment summary derived from loans (totalRepaid / closure).
     * There is no list-all-repayments API; best-effort from lending loan payloads.
     */
    public List<Map<String, Object>> generateCollectionSummary(LocalDate fromDate, LocalDate toDate) {
        try {
            List<Map<String, Object>> loans = fetchAllLoans();
            Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();

            for (Map<String, Object> loan : loans) {
                BigDecimal repaid = toBigDecimal(loan.get("totalRepaid"));
                if (repaid.compareTo(BigDecimal.ZERO) <= 0) continue;

                String dateStr = firstNonBlank(
                        loan.get("closureDate"),
                        loan.get("dueDate"),
                        loan.get("disbursementDate"));
                if (dateStr == null || dateStr.isBlank()) continue;

                LocalDate loanDate;
                try {
                    loanDate = LocalDate.parse(dateStr.length() >= 10 ? dateStr.substring(0, 10) : dateStr);
                } catch (Exception ignored) {
                    continue;
                }
                if (fromDate != null && loanDate.isBefore(fromDate)) continue;
                if (toDate != null && loanDate.isAfter(toDate)) continue;

                String productType = String.valueOf(loan.getOrDefault("productType", "N/A"));
                String key = loanDate + "|" + productType;
                Map<String, Object> row = grouped.computeIfAbsent(key, k -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("date", loanDate.toString());
                    m.put("productType", productType);
                    m.put("loanCount", 0L);
                    m.put("totalCollected", BigDecimal.ZERO);
                    m.put("totalOutstanding", BigDecimal.ZERO);
                    return m;
                });
                row.put("loanCount", ((Number) row.get("loanCount")).longValue() + 1);
                row.put("totalCollected", toBigDecimal(row.get("totalCollected")).add(repaid));
                row.put("totalOutstanding",
                        toBigDecimal(row.get("totalOutstanding")).add(toBigDecimal(loan.get("outstandingAmount"))));
            }
            return new ArrayList<>(grouped.values());
        } catch (Exception e) {
            log.error("Failed to generate collection summary: {}", e.getMessage());
            return List.of();
        }
    }

    /** Program utilization from program payloads + loan outstanding/disbursed. */
    public List<Map<String, Object>> generateProgramUtilization(String programIdFilter) {
        try {
            List<Map<String, Object>> programs = fetchAllPrograms();
            List<Map<String, Object>> loans = fetchAllLoans();

            Map<String, BigDecimal> outstandingByProgram = new HashMap<>();
            Map<String, BigDecimal> disbursedByProgram = new HashMap<>();
            Map<String, Long> activeByProgram = new HashMap<>();
            for (Map<String, Object> loan : loans) {
                String pid = loan.get("programId") != null ? loan.get("programId").toString() : null;
                if (pid == null) continue;
                BigDecimal outstanding = toBigDecimal(loan.get("outstandingAmount"));
                BigDecimal disbursed = toBigDecimal(loan.get("disbursedAmount"));
                outstandingByProgram.merge(pid, outstanding, BigDecimal::add);
                disbursedByProgram.merge(pid, disbursed, BigDecimal::add);
                String status = String.valueOf(loan.get("status"));
                if (Set.of("DISBURSED", "REPAYMENT_DUE", "OVERDUE").contains(status)) {
                    activeByProgram.merge(pid, 1L, Long::sum);
                }
            }

            List<Map<String, Object>> rows = new ArrayList<>();
            for (Map<String, Object> program : programs) {
                String pid = program.get("id") != null ? program.get("id").toString() : null;
                if (pid == null) continue;
                if (programIdFilter != null && !programIdFilter.isBlank() && !programIdFilter.equals(pid)) continue;

                BigDecimal programLimit = toBigDecimal(program.get("programLimit"));
                BigDecimal utilized = toBigDecimal(program.get("utilizedLimit"));
                if (utilized.compareTo(BigDecimal.ZERO) == 0) {
                    utilized = outstandingByProgram.getOrDefault(pid, BigDecimal.ZERO);
                }
                BigDecimal available = toBigDecimal(program.get("availableLimit"));
                if (available.compareTo(BigDecimal.ZERO) == 0 && programLimit.compareTo(BigDecimal.ZERO) > 0) {
                    available = programLimit.subtract(utilized);
                }
                double utilizationPercent = 0.0;
                if (programLimit.compareTo(BigDecimal.ZERO) > 0) {
                    utilizationPercent = utilized.multiply(BigDecimal.valueOf(100))
                            .divide(programLimit, 2, RoundingMode.HALF_UP)
                            .doubleValue();
                }

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("programId", pid);
                row.put("programCode", program.getOrDefault("programCode", "N/A"));
                row.put("programName", program.getOrDefault("programName", "N/A"));
                row.put("productType", String.valueOf(program.getOrDefault("productType", "N/A")));
                row.put("status", String.valueOf(program.getOrDefault("status", "N/A")));
                row.put("programLimit", programLimit);
                row.put("totalUtilized", utilized);
                row.put("available", available);
                row.put("utilizationPercent", utilizationPercent);
                row.put("activeLoans", activeByProgram.getOrDefault(pid, 0L));
                row.put("totalDisbursed", disbursedByProgram.getOrDefault(pid, BigDecimal.ZERO));
                rows.add(row);
            }
            return rows;
        } catch (Exception e) {
            log.error("Failed to generate program utilization: {}", e.getMessage());
            return List.of();
        }
    }

    /** NPA report: 90+ DPD and written-off loans. */
    public List<Map<String, Object>> generateNpaReport() {
        try {
            List<Map<String, Object>> loans = fetchAllLoans();
            List<Map<String, Object>> programs = fetchAllPrograms();
            Map<String, Map<String, Object>> programMap = programs.stream()
                    .filter(p -> p.get("id") != null)
                    .collect(Collectors.toMap(p -> p.get("id").toString(), p -> p, (a, b) -> a));

            List<Map<String, Object>> rows = new ArrayList<>();
            for (Map<String, Object> loan : loans) {
                int dpd = loan.get("dpd") != null ? ((Number) loan.get("dpd")).intValue() : 0;
                String status = String.valueOf(loan.getOrDefault("status", ""));
                boolean isNpa = dpd >= 90 || "WRITTEN_OFF".equals(status)
                        || ("OVERDUE".equals(status) && dpd >= 90);
                if (!isNpa) continue;

                String programId = loan.get("programId") != null ? loan.get("programId").toString() : "";
                Map<String, Object> program = programMap.getOrDefault(programId, Map.of());

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("loanNumber", loan.getOrDefault("loanNumber", ""));
                row.put("borrowerId", loan.get("borrowerId") != null ? loan.get("borrowerId").toString() : "");
                row.put("programCode", program.getOrDefault("programCode", "N/A"));
                row.put("productType", loan.getOrDefault("productType", "N/A"));
                row.put("status", status);
                row.put("outstandingAmount", toBigDecimal(loan.get("outstandingAmount")));
                row.put("dpd", dpd);
                row.put("dpdBucket", getDpdBucket(dpd));
                row.put("dueDate", loan.get("dueDate") != null ? loan.get("dueDate").toString() : "");
                rows.add(row);
            }
            rows.sort((a, b) -> Integer.compare(
                    ((Number) b.getOrDefault("dpd", 0)).intValue(),
                    ((Number) a.getOrDefault("dpd", 0)).intValue()));
            return rows;
        } catch (Exception e) {
            log.error("Failed to generate NPA report: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Invoice discounting pipeline status counts.
     * Prefers program-service invoices; falls back to INVOICE_DISCOUNTING loan status counts.
     */
    public List<Map<String, Object>> generateInvoicePipeline() {
        try {
            List<Map<String, Object>> invoices = fetchAllInvoicesBestEffort();
            if (!invoices.isEmpty()) {
                Map<String, Long> counts = new LinkedHashMap<>();
                Map<String, BigDecimal> amounts = new LinkedHashMap<>();
                for (Map<String, Object> inv : invoices) {
                    String status = String.valueOf(inv.getOrDefault("status", "UNKNOWN"));
                    counts.merge(status, 1L, Long::sum);
                    amounts.merge(status, toBigDecimal(inv.get("invoiceAmount") != null
                            ? inv.get("invoiceAmount") : inv.get("amount")), BigDecimal::add);
                }
                List<Map<String, Object>> rows = new ArrayList<>();
                for (Map.Entry<String, Long> e : counts.entrySet()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("entityType", "INVOICE");
                    row.put("status", e.getKey());
                    row.put("count", e.getValue());
                    row.put("totalAmount", amounts.getOrDefault(e.getKey(), BigDecimal.ZERO));
                    rows.add(row);
                }
                return rows;
            }

            List<Map<String, Object>> loans = fetchAllLoans();
            Map<String, Long> counts = new LinkedHashMap<>();
            Map<String, BigDecimal> amounts = new LinkedHashMap<>();
            for (Map<String, Object> loan : loans) {
                String productType = String.valueOf(loan.getOrDefault("productType", ""));
                if (!"INVOICE_DISCOUNTING".equals(productType) && !"ID".equalsIgnoreCase(productType)) continue;
                String status = String.valueOf(loan.getOrDefault("status", "UNKNOWN"));
                counts.merge(status, 1L, Long::sum);
                amounts.merge(status, toBigDecimal(loan.get("requestedAmount")), BigDecimal::add);
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Map.Entry<String, Long> e : counts.entrySet()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("entityType", "LOAN_INVOICE_DISCOUNTING");
                row.put("status", e.getKey());
                row.put("count", e.getValue());
                row.put("totalAmount", amounts.getOrDefault(e.getKey(), BigDecimal.ZERO));
                rows.add(row);
            }
            return rows;
        } catch (Exception e) {
            log.error("Failed to generate invoice pipeline: {}", e.getMessage());
            return List.of();
        }
    }

    /** Anchor / borrower onboarding funnel by status; falls back to loan status counts. */
    public List<Map<String, Object>> generateOnboardingFunnel() {
        try {
            List<Map<String, Object>> anchors = fetchListBestEffort("http://program-service/api/v1/anchors");
            List<Map<String, Object>> borrowers = fetchListBestEffort("http://program-service/api/v1/borrowers");

            List<Map<String, Object>> rows = new ArrayList<>();
            if (!anchors.isEmpty() || !borrowers.isEmpty()) {
                rows.addAll(statusCountRows("ANCHOR", anchors));
                rows.addAll(statusCountRows("BORROWER", borrowers));
                return rows;
            }

            List<Map<String, Object>> loans = fetchAllLoans();
            Map<String, Long> byStatus = loans.stream()
                    .collect(Collectors.groupingBy(
                            l -> String.valueOf(l.getOrDefault("status", "UNKNOWN")),
                            LinkedHashMap::new,
                            Collectors.counting()));
            for (Map.Entry<String, Long> e : byStatus.entrySet()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("entityType", "LOAN");
                row.put("status", e.getKey());
                row.put("count", e.getValue());
                rows.add(row);
            }
            return rows;
        } catch (Exception e) {
            log.error("Failed to generate onboarding funnel: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Workbench / maker-checker pending queues: DRAFT / PENDING / REQUESTED style statuses
     * across programs, sub-programs, anchors, borrowers, and loans.
     */
    public List<Map<String, Object>> generateWorkbenchPending() {
        try {
            List<Map<String, Object>> rows = new ArrayList<>();

            addPendingCounts(rows, "PROGRAM", fetchAllPrograms(),
                    Set.of("DRAFT", "PENDING_L2", "SENT_BACK", "PENDING"));
            addPendingCounts(rows, "SUB_PROGRAM",
                    fetchListBestEffort("http://program-service/api/v1/sub-programs"),
                    Set.of("DRAFT", "PENDING", "UNDER_REVIEW"));
            addPendingCounts(rows, "ANCHOR",
                    fetchListBestEffort("http://program-service/api/v1/anchors"),
                    Set.of("DRAFT", "UNDER_REVIEW", "PENDING"));
            addPendingCounts(rows, "BORROWER",
                    fetchListBestEffort("http://program-service/api/v1/borrowers"),
                    Set.of("PENDING_KYC", "PENDING", "DRAFT"));
            addPendingCounts(rows, "LOAN", fetchAllLoans(),
                    Set.of("REQUESTED", "PENDING", "DRAFT", "DISBURSEMENT_PENDING", "ELIGIBILITY_CHECK"));

            return rows;
        } catch (Exception e) {
            log.error("Failed to generate workbench pending: {}", e.getMessage());
            return List.of();
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

    private List<Map<String, Object>> fetchAllLoans() {
        return fetchListBestEffort("http://lending-service/api/v1/loans");
    }

    private List<Map<String, Object>> fetchAllPrograms() {
        return fetchListBestEffort("http://program-service/api/v1/programs");
    }

    /** Best-effort invoices list (large page); empty on auth/network failure. */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchAllInvoicesBestEffort() {
        try {
            HttpEntity<Void> entity = new HttpEntity<>(ReportServiceInternalHeaders.trustedInternalHeaders());
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    "http://program-service/api/v1/invoices?page=0&size=500",
                    HttpMethod.GET, entity,
                    new ParameterizedTypeReference<>() {});
            Map<String, Object> body = response.getBody();
            if (body != null && body.containsKey("data")) {
                Object data = body.get("data");
                if (data instanceof List) return (List<Map<String, Object>>) data;
            }
        } catch (Exception e) {
            log.warn("Invoice list unavailable for reports (will fall back to loans): {}", e.getMessage());
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchListBestEffort(String url) {
        try {
            HttpEntity<Void> entity = new HttpEntity<>(ReportServiceInternalHeaders.trustedInternalHeaders());
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity,
                    new ParameterizedTypeReference<>() {});
            Map<String, Object> body = response.getBody();
            if (body != null && body.containsKey("data")) {
                Object data = body.get("data");
                if (data instanceof List) return (List<Map<String, Object>>) data;
            }
        } catch (Exception e) {
            log.warn("Report fetch failed for {}: {}", url, e.getMessage());
        }
        return List.of();
    }

    private List<Map<String, Object>> statusCountRows(String entityType, List<Map<String, Object>> items) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Map<String, Object> item : items) {
            Object statusObj = item.get("status");
            String status = statusObj != null ? statusObj.toString() : "UNKNOWN";
            counts.merge(status, 1L, Long::sum);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, Long> e : counts.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("entityType", entityType);
            row.put("status", e.getKey());
            row.put("count", e.getValue());
            rows.add(row);
        }
        return rows;
    }

    private void addPendingCounts(
            List<Map<String, Object>> rows,
            String entityType,
            List<Map<String, Object>> items,
            Set<String> pendingStatuses) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Map<String, Object> item : items) {
            Object statusObj = item.get("status");
            if (statusObj == null) continue;
            String status = statusObj.toString().toUpperCase(Locale.ROOT);
            if (pendingStatuses.contains(status)) {
                counts.merge(status, 1L, Long::sum);
            }
        }
        for (Map.Entry<String, Long> e : counts.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("entityType", entityType);
            row.put("status", e.getKey());
            row.put("count", e.getValue());
            rows.add(row);
        }
    }

    private String firstNonBlank(Object... values) {
        for (Object v : values) {
            if (v == null) continue;
            String s = v.toString();
            if (!s.isBlank() && !"null".equalsIgnoreCase(s)) return s;
        }
        return null;
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
