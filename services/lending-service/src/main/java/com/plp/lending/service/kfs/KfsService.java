package com.plp.lending.service.kfs;

import com.plp.lending.model.entity.Loan;
import com.plp.lending.repository.LoanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class KfsService {

    private final LoanRepository loanRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy");

    public String generateKfs(UUID loanId) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new RuntimeException("Loan not found: " + loanId));
        return buildKfsHtml(loan);
    }

    private String buildKfsHtml(Loan loan) {
        BigDecimal principal = loan.getSanctionedAmount() != null ? loan.getSanctionedAmount() : loan.getRequestedAmount();
        BigDecimal interest = loan.getInterestAmount() != null ? loan.getInterestAmount() : BigDecimal.ZERO;
        BigDecimal fee = loan.getProcessingFee() != null ? loan.getProcessingFee() : BigDecimal.ZERO;
        BigDecimal totalRepayable = loan.getTotalRepayable() != null ? loan.getTotalRepayable() : principal.add(interest).add(fee);
        BigDecimal annualRate = loan.getInterestRate() != null ? loan.getInterestRate() : BigDecimal.ZERO;
        int tenure = loan.getTenureDays();

        BigDecimal apr = calculateApr(principal, totalRepayable, tenure);
        String productLabel = "PAY_DAY_LOAN".equals(loan.getProductType()) ? "Pay Day Loan (Earned Wage Access)" : "Invoice Discounting (Purchase Bill Discounting)";
        String today = LocalDate.now().format(DATE_FMT);
        String dueDate = loan.getDueDate() != null ? loan.getDueDate().format(DATE_FMT) : "N/A";

        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                <meta charset="UTF-8">
                <title>Key Fact Statement — %s</title>
                <style>
                  body { font-family: 'Segoe UI', Arial, sans-serif; max-width: 800px; margin: 40px auto; padding: 20px; color: #333; }
                  h1 { text-align: center; color: #1e3a5f; border-bottom: 3px solid #1e3a5f; padding-bottom: 10px; }
                  h2 { color: #2c5282; margin-top: 30px; }
                  .subtitle { text-align: center; color: #666; font-size: 14px; margin-bottom: 30px; }
                  table { width: 100%%; border-collapse: collapse; margin: 15px 0; }
                  th, td { border: 1px solid #ccc; padding: 10px 14px; text-align: left; }
                  th { background: #f0f4f8; font-weight: 600; width: 40%%; }
                  .amount { font-weight: bold; color: #1e3a5f; }
                  .highlight { background: #fff3cd; font-weight: bold; }
                  .footer { margin-top: 40px; border-top: 2px solid #ccc; padding-top: 20px; font-size: 12px; color: #666; }
                  .warning { background: #fef3cd; border: 1px solid #ffc107; padding: 12px; border-radius: 4px; margin: 20px 0; font-size: 13px; }
                  @media print { body { margin: 0; } .no-print { display: none; } }
                </style>
                </head>
                <body>
                <h1>KEY FACT STATEMENT (KFS)</h1>
                <p class="subtitle">As per RBI Digital Lending Directions 2025 — Chapter III, Section 8</p>

                <h2>1. Loan Details</h2>
                <table>
                  <tr><th>Loan Number</th><td class="amount">%s</td></tr>
                  <tr><th>Product Type</th><td>%s</td></tr>
                  <tr><th>Date of Issue</th><td>%s</td></tr>
                  <tr><th>Loan Amount (Principal)</th><td class="amount">₹%s</td></tr>
                  <tr><th>Interest Rate (p.a.)</th><td>%s%%</td></tr>
                  <tr><th>Interest Method</th><td>%s</td></tr>
                  <tr><th>Tenure</th><td>%d days</td></tr>
                  <tr><th>Due Date</th><td>%s</td></tr>
                </table>

                <h2>2. Charges & Fees</h2>
                <table>
                  <tr><th>Interest Amount</th><td class="amount">₹%s</td></tr>
                  <tr><th>Processing Fee</th><td>₹%s</td></tr>
                  <tr><th>Penal Interest (on overdue)</th><td>As per program terms (applied daily on overdue amount)</td></tr>
                  <tr><th>Other Charges</th><td>Nil</td></tr>
                </table>

                <h2>3. Total Cost of Credit</h2>
                <table>
                  <tr class="highlight"><th>Total Amount Repayable</th><td class="amount">₹%s</td></tr>
                  <tr class="highlight"><th>Annual Percentage Rate (APR)</th><td class="amount">%s%%</td></tr>
                </table>

                <div class="warning">
                  <strong>Important:</strong> The APR is the annualized total cost of credit, including all charges.
                  As per RBI guidelines, this figure helps you compare the effective cost across different lenders.
                </div>

                <h2>4. Repayment Schedule</h2>
                <table>
                  <tr><th>Repayment Type</th><td>Bullet (single payment on due date)</td></tr>
                  <tr><th>Repayment Date</th><td>%s</td></tr>
                  <tr><th>Repayment Mode</th><td>%s</td></tr>
                  <tr><th>Amount Due on Repayment Date</th><td class="amount">₹%s</td></tr>
                </table>

                <h2>5. Cooling-Off Period</h2>
                <table>
                  <tr><th>Cooling-Off/Look-Up Period</th><td>%s</td></tr>
                  <tr><th>Exit Penalty</th><td>None (within cooling-off period)</td></tr>
                </table>

                <h2>6. Grievance Redressal</h2>
                <table>
                  <tr><th>Grievance Redressal Officer</th><td>Compliance Department, Program Lending Platform</td></tr>
                  <tr><th>Contact</th><td>grievance@plp.com | 1800-XXX-XXXX</td></tr>
                  <tr><th>Resolution SLA</th><td>30 days from receipt of complaint</td></tr>
                  <tr><th>RBI Ombudsman</th><td>If unresolved in 30 days, you may approach the RBI Integrated Ombudsman at <br>https://cms.rbi.org.in</td></tr>
                </table>

                <h2>7. Important Disclosures</h2>
                <ul>
                  <li>This loan is originated and disbursed by the Regulated Entity (RE) / Lender as per RBI norms.</li>
                  <li>The loan amount will be disbursed directly to your registered bank account.</li>
                  <li>All data collected is stored securely in India as per DPDP Act 2023.</li>
                  <li>Loan details will be reported to Credit Information Companies (CIBIL/Equifax/Experian/CRIF).</li>
                  <li>Late payment may attract penal interest and adversely impact your credit score.</li>
                </ul>

                <div class="footer">
                  <p><strong>Borrower Acknowledgment:</strong> I have read and understood all terms stated in this Key Fact Statement.
                  I agree to the loan terms and authorize the deduction/collection of repayment as described above.</p>
                  <br>
                  <table style="border:none;">
                    <tr style="border:none;">
                      <td style="border:none;">Borrower Signature: _______________</td>
                      <td style="border:none;">Date: _______________</td>
                    </tr>
                  </table>
                  <p style="text-align:center; margin-top:20px;">Generated on %s | Loan: %s | Platform: Program Lending Platform</p>
                </div>
                </body>
                </html>
                """.formatted(
                loan.getLoanNumber(),
                loan.getLoanNumber(),
                productLabel,
                today,
                formatAmount(principal),
                annualRate.setScale(2, RoundingMode.HALF_UP),
                loan.getInterestMethod() != null ? loan.getInterestMethod() : "FLAT",
                tenure,
                dueDate,
                formatAmount(interest),
                formatAmount(fee),
                formatAmount(totalRepayable),
                apr.setScale(2, RoundingMode.HALF_UP),
                dueDate,
                "PAY_DAY_LOAN".equals(loan.getProductType()) ?
                        "Salary deduction on pay day / NACH auto-debit" :
                        "Direct payment on invoice due date / NACH auto-debit",
                formatAmount(totalRepayable),
                tenure > 7 ? "3 days from disbursement date" : "Not applicable (tenure ≤ 7 days)",
                today,
                loan.getLoanNumber()
        );
    }

    private BigDecimal calculateApr(BigDecimal principal, BigDecimal totalRepayable, int tenureDays) {
        if (principal.compareTo(BigDecimal.ZERO) == 0 || tenureDays == 0) return BigDecimal.ZERO;
        BigDecimal totalCost = totalRepayable.subtract(principal);
        return totalCost
                .multiply(BigDecimal.valueOf(365))
                .multiply(BigDecimal.valueOf(100))
                .divide(principal.multiply(BigDecimal.valueOf(tenureDays)), 2, RoundingMode.HALF_UP);
    }

    private String formatAmount(BigDecimal amount) {
        if (amount == null) return "0.00";
        return String.format("%,.2f", amount);
    }
}
