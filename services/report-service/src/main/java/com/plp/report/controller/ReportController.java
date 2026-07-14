package com.plp.report.controller;

import com.plp.report.model.dto.DisbursementSummary;
import com.plp.report.model.dto.OverdueReport;
import com.plp.report.model.dto.PortfolioSummary;
import com.plp.report.model.entity.ReportDefinition;
import com.plp.report.service.ReportGeneratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportGeneratorService reportGeneratorService;

    @GetMapping("/disbursement-summary")
    public ResponseEntity<Map<String, Object>> disbursementSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        List<DisbursementSummary> data = reportGeneratorService.generateDisbursementSummary(fromDate, toDate);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", data));
    }

    @GetMapping("/portfolio-summary")
    public ResponseEntity<Map<String, Object>> portfolioSummary() {
        List<PortfolioSummary> data = reportGeneratorService.generatePortfolioSummary();
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", data));
    }

    @GetMapping("/overdue")
    public ResponseEntity<Map<String, Object>> overdueReport() {
        List<OverdueReport> data = reportGeneratorService.generateOverdueReport();
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", data));
    }

    @GetMapping("/collection-summary")
    public ResponseEntity<Map<String, Object>> collectionSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        List<Map<String, Object>> data = reportGeneratorService.generateCollectionSummary(fromDate, toDate);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", data));
    }

    @GetMapping("/program-utilization")
    public ResponseEntity<Map<String, Object>> programUtilization(
            @RequestParam(required = false) String programId) {
        List<Map<String, Object>> data = reportGeneratorService.generateProgramUtilization(programId);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", data));
    }

    @GetMapping("/npa-report")
    public ResponseEntity<Map<String, Object>> npaReport() {
        List<Map<String, Object>> data = reportGeneratorService.generateNpaReport();
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", data));
    }

    @GetMapping("/invoice-pipeline")
    public ResponseEntity<Map<String, Object>> invoicePipeline() {
        List<Map<String, Object>> data = reportGeneratorService.generateInvoicePipeline();
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", data));
    }

    @GetMapping("/onboarding-funnel")
    public ResponseEntity<Map<String, Object>> onboardingFunnel() {
        List<Map<String, Object>> data = reportGeneratorService.generateOnboardingFunnel();
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", data));
    }

    @GetMapping("/workbench-pending")
    public ResponseEntity<Map<String, Object>> workbenchPending() {
        List<Map<String, Object>> data = reportGeneratorService.generateWorkbenchPending();
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", data));
    }

    @GetMapping("/dashboard-stats")
    public ResponseEntity<Map<String, Object>> dashboardStats() {
        Map<String, Object> data = reportGeneratorService.generateDashboardStats();
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", data));
    }

    @GetMapping("/definitions")
    public ResponseEntity<Map<String, Object>> getDefinitions() {
        List<ReportDefinition> defs = reportGeneratorService.getReportDefinitions();
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", defs));
    }

    @GetMapping("/export/disbursement-summary")
    public ResponseEntity<String> exportDisbursementSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        List<DisbursementSummary> data = reportGeneratorService.generateDisbursementSummary(fromDate, toDate);
        StringBuilder csv = new StringBuilder("Date,Product Type,Loan Count,Total Disbursed,Total Approved\n");
        for (DisbursementSummary s : data) {
            csv.append(String.format("%s,%s,%d,%s,%s\n",
                    s.getDate(), s.getProductType(), s.getLoanCount(),
                    s.getTotalDisbursed(), s.getTotalApproved()));
        }
        return csvResponse(csv.toString(), "disbursement_summary.csv");
    }

    @GetMapping("/export/portfolio-summary")
    public ResponseEntity<String> exportPortfolioSummary() {
        List<PortfolioSummary> data = reportGeneratorService.generatePortfolioSummary();
        StringBuilder csv = new StringBuilder("Program Code,Program Name,Product Type,Total Loans,Active Loans,Overdue Loans,Total Disbursed,Total Outstanding,Total Overdue,NPA %\n");
        for (PortfolioSummary s : data) {
            csv.append(String.format("%s,%s,%s,%d,%d,%d,%s,%s,%s,%.2f\n",
                    s.getProgramCode(), s.getProgramName(), s.getProductType(),
                    s.getTotalLoans(), s.getActiveLoans(), s.getOverdueLoans(),
                    s.getTotalDisbursed(), s.getTotalOutstanding(), s.getTotalOverdue(), s.getNpaPercent()));
        }
        return csvResponse(csv.toString(), "portfolio_summary.csv");
    }

    @GetMapping("/export/overdue")
    public ResponseEntity<String> exportOverdueReport() {
        List<OverdueReport> data = reportGeneratorService.generateOverdueReport();
        StringBuilder csv = new StringBuilder("Loan Number,Borrower,Program Code,Product Type,Outstanding Amount,DPD,DPD Bucket,Due Date\n");
        for (OverdueReport r : data) {
            csv.append(String.format("%s,%s,%s,%s,%s,%d,%s,%s\n",
                    r.getLoanNumber(), r.getBorrowerName(), r.getProgramCode(),
                    r.getProductType(), r.getOutstandingAmount(), r.getDpd(),
                    r.getDpdBucket(), r.getDueDate()));
        }
        return csvResponse(csv.toString(), "overdue_report.csv");
    }

    @GetMapping("/export/collection-summary")
    public ResponseEntity<String> exportCollectionSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return csvResponse(
                mapsToCsv(reportGeneratorService.generateCollectionSummary(fromDate, toDate)),
                "collection_summary.csv");
    }

    @GetMapping("/export/program-utilization")
    public ResponseEntity<String> exportProgramUtilization(
            @RequestParam(required = false) String programId) {
        return csvResponse(
                mapsToCsv(reportGeneratorService.generateProgramUtilization(programId)),
                "program_utilization.csv");
    }

    @GetMapping("/export/npa-report")
    public ResponseEntity<String> exportNpaReport() {
        return csvResponse(mapsToCsv(reportGeneratorService.generateNpaReport()), "npa_report.csv");
    }

    @GetMapping("/export/invoice-pipeline")
    public ResponseEntity<String> exportInvoicePipeline() {
        return csvResponse(mapsToCsv(reportGeneratorService.generateInvoicePipeline()), "invoice_pipeline.csv");
    }

    @GetMapping("/export/onboarding-funnel")
    public ResponseEntity<String> exportOnboardingFunnel() {
        return csvResponse(mapsToCsv(reportGeneratorService.generateOnboardingFunnel()), "onboarding_funnel.csv");
    }

    @GetMapping("/export/workbench-pending")
    public ResponseEntity<String> exportWorkbenchPending() {
        return csvResponse(mapsToCsv(reportGeneratorService.generateWorkbenchPending()), "workbench_pending.csv");
    }

    private static ResponseEntity<String> csvResponse(String body, String filename) {
        return ResponseEntity.ok()
                .header("Content-Type", "text/csv")
                .header("Content-Disposition", "attachment; filename=" + filename)
                .body(body);
    }

    private static String mapsToCsv(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return "message\nNo data\n";
        }
        List<String> headers = rows.stream()
                .map(Map::keySet)
                .flatMap(Collection::stream)
                .distinct()
                .collect(Collectors.toList());
        StringBuilder csv = new StringBuilder();
        csv.append(String.join(",", headers)).append('\n');
        for (Map<String, Object> row : rows) {
            csv.append(headers.stream()
                    .map(h -> csvEscape(row.get(h)))
                    .collect(Collectors.joining(",")));
            csv.append('\n');
        }
        return csv.toString();
    }

    private static String csvEscape(Object value) {
        if (value == null) return "";
        String s = value.toString();
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
