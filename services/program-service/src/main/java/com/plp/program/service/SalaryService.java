package com.plp.program.service;

import com.plp.program.model.entity.Borrower;
import com.plp.program.model.entity.EmployeeSalaryData;
import com.plp.program.model.entity.Program;
import com.plp.program.model.enums.SalarySlipStatus;
import com.plp.program.repository.BorrowerRepository;
import com.plp.program.repository.EmployeeSalaryDataRepository;
import com.plp.program.repository.ProgramRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SalaryService {

    private final EmployeeSalaryDataRepository salaryRepository;
    private final BorrowerRepository borrowerRepository;
    private final ProgramRepository programRepository;

    public List<EmployeeSalaryData> getAllByAnchorIdOrdered(UUID anchorId) {
        return salaryRepository.findByAnchorIdOrderByPeriodToDesc(anchorId);
    }

    public Optional<EmployeeSalaryData> findById(UUID id) {
        return salaryRepository.findById(id);
    }

    /**
     * Trusted update from lending-service: moves persisted slip status through the loan lifecycle.
     */
    @Transactional
    public EmployeeSalaryData updateSlipStatus(UUID slipId, SalarySlipStatus newStatus) {
        EmployeeSalaryData row =
                salaryRepository.findById(slipId).orElseThrow(() -> new RuntimeException("Salary slip not found: " + slipId));
        row.setSlipStatus(newStatus);
        return salaryRepository.save(row);
    }

    @Transactional
    public EmployeeSalaryData createOrUpdateSalary(EmployeeSalaryData salaryData) {
        applyPeriodBoundsIfMissing(salaryData);
        validatePayPeriodNotFuture(salaryData.getPeriodFrom());

        Optional<EmployeeSalaryData> existing =
                salaryRepository.findByBorrowerIdAndPayPeriod(salaryData.getBorrowerId(), salaryData.getPayPeriod());
        if (existing.isPresent()) {
            EmployeeSalaryData e = existing.get();
            validateNoOverlap(salaryData.getBorrowerId(), e.getId(), salaryData.getPeriodFrom(), salaryData.getPeriodTo());
            e.setGrossSalary(salaryData.getGrossSalary());
            e.setNetSalary(salaryData.getNetSalary());
            e.setDaysWorked(salaryData.getDaysWorked());
            e.setTotalWorkingDays(salaryData.getTotalWorkingDays());
            e.setDeductions(salaryData.getDeductions());
            e.setAccumulatedSalary(salaryData.getAccumulatedSalary());
            e.setEligibleAmount(salaryData.getEligibleAmount());
            e.setEligibilityPercent(salaryData.getEligibilityPercent());
            e.setSource(salaryData.getSource());
            e.setVerified(salaryData.getVerified());
            e.setVerifiedAt(salaryData.getVerifiedAt());
            e.setVerifiedBy(salaryData.getVerifiedBy());
            e.setPeriodFrom(salaryData.getPeriodFrom());
            e.setPeriodTo(salaryData.getPeriodTo());
            if (salaryData.getExternalReferenceNumber() != null) {
                e.setExternalReferenceNumber(salaryData.getExternalReferenceNumber().trim().isEmpty()
                        ? null
                        : salaryData.getExternalReferenceNumber().trim());
            }
            computeEligibility(e);
            return salaryRepository.save(e);
        }

        validateNoOverlap(salaryData.getBorrowerId(), null, salaryData.getPeriodFrom(), salaryData.getPeriodTo());

        salaryData.setSalarySlipNumber(nextSalarySlipNumber());
        salaryData.setSlipStatus(SalarySlipStatus.AVAILABLE_FOR_LOAN);
        computeEligibility(salaryData);

        return salaryRepository.save(salaryData);
    }

    @Transactional
    public List<EmployeeSalaryData> uploadSalaryCsv(UUID anchorId, UUID programId, String payPeriod,
                                                      InputStream csvStream, UUID uploadedBy) {
        Program program = programRepository.findById(programId)
                .orElseThrow(() -> new RuntimeException("Program not found: " + programId));

        BigDecimal eligibilityPct;
        if (program.getEligibilityRules() != null && program.getEligibilityRules().containsKey("eligibilityPercent")) {
            eligibilityPct = new BigDecimal(program.getEligibilityRules().get("eligibilityPercent").toString());
        } else {
            eligibilityPct = new BigDecimal("50.00");
        }

        LocalDate periodFrom = monthStart(payPeriod);
        LocalDate periodTo = monthEnd(payPeriod);
        validatePayPeriodNotFuture(periodFrom);
        // CSV upload is for one pay period — borrower overlap checked per row

        List<EmployeeSalaryData> results = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(csvStream))) {
            String header = reader.readLine();
            if (header == null) {
                throw new RuntimeException("CSV file is empty");
            }
            String line;
            int rowNum = 1;
            while ((line = reader.readLine()) != null) {
                rowNum++;
                if (line.trim().isEmpty()) {
                    continue;
                }
                String[] parts = line.split(",", -1);
                if (parts.length < 5) {
                    log.warn("Skipping row {}: insufficient columns", rowNum);
                    continue;
                }

                String employeeCode = parts[0].trim();
                String borrowerCode = parts[1].trim();
                BigDecimal grossSalary = new BigDecimal(parts[2].trim().replace("\"", ""));
                BigDecimal netSalary = new BigDecimal(parts[3].trim().replace("\"", ""));
                int daysWorked = Integer.parseInt(parts[4].trim());
                int totalWorkingDays = parts.length > 5 ? Integer.parseInt(parts[5].trim()) : 30;
                BigDecimal deductions =
                        parts.length > 6 ? new BigDecimal(parts[6].trim().replace("\"", "")) : BigDecimal.ZERO;

                if (totalWorkingDays <= 0) {
                    log.warn("Skipping row {}: totalWorkingDays must be > 0, got {}", rowNum, totalWorkingDays);
                    continue;
                }

                Borrower borrower = borrowerRepository.findByBorrowerCode(borrowerCode).orElse(null);
                if (borrower == null) {
                    log.warn("Skipping row {}: borrower not found for code {}", rowNum, borrowerCode);
                    continue;
                }

                BigDecimal accumulated = netSalary
                        .multiply(BigDecimal.valueOf(daysWorked))
                        .divide(BigDecimal.valueOf(totalWorkingDays), 2, RoundingMode.HALF_UP);
                BigDecimal eligible = accumulated
                        .multiply(eligibilityPct)
                        .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

                EmployeeSalaryData data = EmployeeSalaryData.builder()
                        .borrowerId(borrower.getId())
                        .anchorId(anchorId)
                        .programId(programId)
                        .employeeCode(employeeCode)
                        .payPeriod(payPeriod)
                        .externalReferenceNumber(null)
                        .periodFrom(periodFrom)
                        .periodTo(periodTo)
                        .slipStatus(SalarySlipStatus.AVAILABLE_FOR_LOAN)
                        .grossSalary(grossSalary)
                        .netSalary(netSalary)
                        .daysWorked(daysWorked)
                        .totalWorkingDays(totalWorkingDays)
                        .deductions(deductions)
                        .accumulatedSalary(accumulated)
                        .eligibleAmount(eligible)
                        .eligibilityPercent(eligibilityPct)
                        .source("MANUAL")
                        .verified(true)
                        .verifiedAt(Instant.now())
                        .verifiedBy(uploadedBy)
                        .build();

                results.add(createOrUpdateSalary(data));
            }
        } catch (NumberFormatException e) {
            throw new RuntimeException("Invalid number format in CSV: " + e.getMessage());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Error processing CSV: " + e.getMessage(), e);
        }

        log.info("Salary CSV upload: anchor={} program={} period={} rows={}", anchorId, programId, payPeriod, results.size());
        return results;
    }

    /** Per-row CSV: overlap may double-fire if same borrower twice in file — createOrUpdate merges by pay period */
    private void validateNoOverlap(UUID borrowerId, UUID excludeId, LocalDate from, LocalDate to) {
        if (salaryRepository.countBorrowerPeriodOverlap(borrowerId, excludeId, from, to) > 0) {
            throw new RuntimeException(
                    "Salary slip periods cannot overlap for the same borrower. Adjust pay period or dates.");
        }
    }

    private void applyPeriodBoundsIfMissing(EmployeeSalaryData salaryData) {
        if (salaryData.getPeriodFrom() == null || salaryData.getPeriodTo() == null) {
            LocalDate from = monthStart(salaryData.getPayPeriod());
            LocalDate to = monthEnd(salaryData.getPayPeriod());
            salaryData.setPeriodFrom(from);
            salaryData.setPeriodTo(to);
        }
        if (salaryData.getPeriodTo().isBefore(salaryData.getPeriodFrom())) {
            throw new RuntimeException("periodTo must be on or after periodFrom");
        }
    }

    private static void validatePayPeriodNotFuture(LocalDate periodFrom) {
        LocalDate today = LocalDate.now();
        if (periodFrom.isAfter(today)) {
            throw new RuntimeException("Cannot upload salary for a future pay period");
        }
    }

    public static LocalDate monthStart(String payPeriodYYYYMM) {
        YearMonth ym = YearMonth.parse(payPeriodYYYYMM);
        return ym.atDay(1);
    }

    public static LocalDate monthEnd(String payPeriodYYYYMM) {
        YearMonth ym = YearMonth.parse(payPeriodYYYYMM);
        return ym.atEndOfMonth();
    }

    private String nextSalarySlipNumber() {
        long n = salaryRepository.nextSalarySlipNumberSequence();
        return "SSL-" + String.format("%010d", n);
    }

    public List<EmployeeSalaryData> getByAnchorAndPeriod(UUID anchorId, String payPeriod) {
        return salaryRepository.findByAnchorIdAndPayPeriod(anchorId, payPeriod);
    }

    public List<EmployeeSalaryData> getByBorrower(UUID borrowerId) {
        return salaryRepository.findByBorrowerId(borrowerId);
    }

    public Optional<EmployeeSalaryData> getLatestByBorrower(UUID borrowerId) {
        return salaryRepository.findTopByBorrowerIdOrderByPayPeriodDesc(borrowerId);
    }

    public Optional<EmployeeSalaryData> getByBorrowerAndPeriod(UUID borrowerId, String payPeriod) {
        return salaryRepository.findByBorrowerIdAndPayPeriod(borrowerId, payPeriod);
    }

    private void computeEligibility(EmployeeSalaryData data) {
        if (data.getTotalWorkingDays() == null || data.getTotalWorkingDays() <= 0) {
            throw new RuntimeException("Total working days must be greater than 0");
        }
        if (data.getAccumulatedSalary() == null || data.getAccumulatedSalary().compareTo(BigDecimal.ZERO) == 0) {
            BigDecimal accumulated = data.getNetSalary()
                    .multiply(BigDecimal.valueOf(data.getDaysWorked()))
                    .divide(BigDecimal.valueOf(data.getTotalWorkingDays()), 2, RoundingMode.HALF_UP);
            data.setAccumulatedSalary(accumulated);
        }
        BigDecimal pct = data.getEligibilityPercent() != null ? data.getEligibilityPercent() : new BigDecimal("50.00");
        BigDecimal eligible = data.getAccumulatedSalary()
                .multiply(pct)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        data.setEligibleAmount(eligible);
    }
}
