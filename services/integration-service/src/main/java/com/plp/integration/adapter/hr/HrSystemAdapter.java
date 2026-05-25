package com.plp.integration.adapter.hr;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Adapter interface for HR system integrations.
 * Each HR provider (Darwinbox, Keka, GreytHR, etc.) implements this interface.
 */
public interface HrSystemAdapter {

    /**
     * Fetch current salary details for an employee.
     */
    SalaryInfo fetchSalaryInfo(String employeeId, UUID anchorId);

    /**
     * Get accumulated/earned salary for the current pay cycle.
     */
    BigDecimal getEarnedSalary(String employeeId, UUID anchorId, LocalDate asOfDate);

    /**
     * Verify employment status.
     */
    EmploymentStatus verifyEmployment(String employeeId, UUID anchorId);

    /**
     * Verify employee is still active and fetch attendance data.
     */
    AttendanceInfo getAttendanceInfo(String employeeId, UUID anchorId, LocalDate fromDate, LocalDate toDate);

    record SalaryInfo(
            String employeeId,
            BigDecimal monthlySalary,
            BigDecimal netSalary,
            BigDecimal earnedSalary,
            int daysWorkedInCycle,
            int totalDaysInCycle,
            LocalDate payrollCycleStart,
            LocalDate payrollCycleEnd,
            LocalDate nextPayDate
    ) {}

    record EmploymentStatus(
            String employeeId,
            boolean isActive,
            LocalDate joiningDate,
            String department,
            String designation,
            int monthsOfService
    ) {}

    record AttendanceInfo(
            String employeeId,
            int totalWorkingDays,
            int presentDays,
            int leaveDays,
            BigDecimal attendancePercentage
    ) {}
}
