package com.plp.lending.model.entity;

import com.plp.lending.model.enums.InterestMethod;
import com.plp.lending.model.enums.LoanStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "loans", schema = "plp_lending")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 30)
    private String loanNumber;

    @Column(nullable = false)
    private UUID borrowerId;

    @Column(nullable = false)
    private UUID programId;

    @Column(nullable = false)
    private UUID anchorId;

    @Column(nullable = false, length = 30)
    private String productType;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal requestedAmount;

    @Column(precision = 15, scale = 2)
    private BigDecimal sanctionedAmount;

    @Column(precision = 15, scale = 2)
    private BigDecimal disbursedAmount;

    @Column(precision = 5, scale = 2)
    private BigDecimal interestRate;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private InterestMethod interestMethod = InterestMethod.FLAT;

    @Column(precision = 15, scale = 2)
    private BigDecimal interestAmount;

    @Column(precision = 15, scale = 2)
    private BigDecimal processingFee;

    @Column(precision = 15, scale = 2)
    private BigDecimal totalRepayable;

    @Column(precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalRepaid = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal outstandingAmount = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal penalInterest = BigDecimal.ZERO;

    @Column(nullable = false)
    private Integer tenureDays;

    private LocalDate requestDate;

    private LocalDate sanctionDate;

    private LocalDate disbursementDate;

    private LocalDate dueDate;

    private LocalDate closureDate;

    @Column
    @Builder.Default
    private Integer dpd = 0;

    private UUID invoiceId;

    @Column(name = "sub_program_id")
    private UUID subProgramId;

    /** Encore LMS account id when program lms_entry_in=YES; loanNumber remains PLP business id. */
    @Column(name = "lms_account_id", length = 50)
    private String lmsAccountId;

    /**
     * When true, disbursement may use program borrower_limits even if {@code subProgramId} is absent (pre–sub-program
     * data). New Pay Day / invoice loans should keep this false and always carry {@code subProgramId}.
     */
    @Column(name = "legacy_program_level_limits", nullable = false)
    @Builder.Default
    private boolean legacyProgramLevelLimits = false;

    @Column(name = "salary_data_id")
    private UUID salaryDataId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> eligibilitySnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> kfsData;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private LoanStatus status = LoanStatus.REQUESTED;

    @Column(length = 500)
    private String rejectionReason;

    @Builder.Default
    private boolean autoApproved = false;

    private UUID approvedBy;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
