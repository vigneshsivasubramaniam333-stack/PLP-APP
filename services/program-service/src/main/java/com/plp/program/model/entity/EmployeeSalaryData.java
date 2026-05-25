package com.plp.program.model.entity;

import com.plp.program.model.enums.SalarySlipStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "employee_salary_data", schema = "plp_program",
       uniqueConstraints = @UniqueConstraint(columnNames = {"borrower_id", "pay_period"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeSalaryData {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID borrowerId;

    @Column(nullable = false)
    private UUID anchorId;

    @Column(nullable = false)
    private UUID programId;

    @Column(nullable = false, length = 30)
    private String employeeCode;

    @Column(nullable = false, length = 7)
    private String payPeriod;

    @Column(nullable = false, unique = true, length = 32)
    private String salarySlipNumber;

    @Column(length = 128)
    private String externalReferenceNumber;

    @Column(nullable = false)
    private LocalDate periodFrom;

    @Column(nullable = false)
    private LocalDate periodTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    @Builder.Default
    private SalarySlipStatus slipStatus = SalarySlipStatus.AVAILABLE_FOR_LOAN;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal grossSalary;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal netSalary;

    @Column(nullable = false)
    @Builder.Default
    private Integer daysWorked = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer totalWorkingDays = 30;

    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal accumulatedSalary = BigDecimal.ZERO;

    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal deductions = BigDecimal.ZERO;

    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal eligibleAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal eligibilityPercent = new BigDecimal("50.00");

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String source = "MANUAL";

    @Builder.Default
    private Boolean verified = false;

    private Instant verifiedAt;

    private UUID verifiedBy;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
