package com.plp.integration.adapter.hr;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Mock HR adapter for development/testing.
 * Simulates salary and employment data.
 */
@Slf4j
@Component
public class MockHrAdapter implements HrSystemAdapter {

    @Override
    public SalaryInfo fetchSalaryInfo(String employeeId, UUID anchorId) {
        log.info("Mock HR: Fetching salary info for employee {} under anchor {}", employeeId, anchorId);

        LocalDate cycleStart = LocalDate.now().withDayOfMonth(1);
        LocalDate cycleEnd = cycleStart.plusMonths(1).minusDays(1);
        int daysInCycle = (int) ChronoUnit.DAYS.between(cycleStart, cycleEnd) + 1;
        int daysWorked = (int) ChronoUnit.DAYS.between(cycleStart, LocalDate.now()) + 1;

        BigDecimal monthlySalary = BigDecimal.valueOf(75000);
        BigDecimal dailyRate = monthlySalary.divide(BigDecimal.valueOf(daysInCycle), 2, RoundingMode.HALF_UP);
        BigDecimal earned = dailyRate.multiply(BigDecimal.valueOf(daysWorked));

        return new SalaryInfo(
                employeeId,
                monthlySalary,
                BigDecimal.valueOf(62000),
                earned,
                daysWorked,
                daysInCycle,
                cycleStart,
                cycleEnd,
                cycleEnd.plusDays(1)
        );
    }

    @Override
    public BigDecimal getEarnedSalary(String employeeId, UUID anchorId, LocalDate asOfDate) {
        SalaryInfo info = fetchSalaryInfo(employeeId, anchorId);
        return info.earnedSalary();
    }

    @Override
    public EmploymentStatus verifyEmployment(String employeeId, UUID anchorId) {
        log.info("Mock HR: Verifying employment for {} under anchor {}", employeeId, anchorId);
        return new EmploymentStatus(
                employeeId,
                true,
                LocalDate.now().minusMonths(18),
                "Engineering",
                "Senior Engineer",
                18
        );
    }

    @Override
    public AttendanceInfo getAttendanceInfo(String employeeId, UUID anchorId, LocalDate fromDate, LocalDate toDate) {
        int totalDays = (int) ChronoUnit.DAYS.between(fromDate, toDate);
        int presentDays = (int) (totalDays * 0.92);
        return new AttendanceInfo(
                employeeId,
                totalDays,
                presentDays,
                totalDays - presentDays,
                BigDecimal.valueOf(92.0)
        );
    }
}
