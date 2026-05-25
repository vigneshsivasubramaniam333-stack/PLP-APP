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
import java.util.List;
import java.util.Map;

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
        return ResponseEntity.ok()
                .header("Content-Type", "text/csv")
                .header("Content-Disposition", "attachment; filename=disbursement_summary.csv")
                .body(csv.toString());
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
        return ResponseEntity.ok()
                .header("Content-Type", "text/csv")
                .header("Content-Disposition", "attachment; filename=portfolio_summary.csv")
                .body(csv.toString());
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
        return ResponseEntity.ok()
                .header("Content-Type", "text/csv")
                .header("Content-Disposition", "attachment; filename=overdue_report.csv")
                .body(csv.toString());
    }
}
