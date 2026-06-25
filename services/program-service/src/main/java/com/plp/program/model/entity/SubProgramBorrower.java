package com.plp.program.model.entity;

import com.plp.program.model.enums.PaymentMethodMode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sub_program_borrowers", schema = "plp_program",
        uniqueConstraints = @UniqueConstraint(columnNames = {"sub_program_id", "borrower_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubProgramBorrower {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "sub_program_id", nullable = false)
    private UUID subProgramId;

    @Column(name = "borrower_id", nullable = false)
    private UUID borrowerId;

    @Column(name = "borrower_limit", precision = 19, scale = 2)
    private BigDecimal borrowerLimit;

    @Column(name = "utilized_limit", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal utilizedLimit = BigDecimal.ZERO;

    @Column(name = "available_limit", precision = 19, scale = 2)
    private BigDecimal availableLimit;

    @Column(name = "interest_rate", precision = 7, scale = 4)
    private BigDecimal interestRate;

    @Column(name = "discount_margin_percent", precision = 7, scale = 4)
    private BigDecimal discountMarginPercent;

    @Column(name = "credit_period_days")
    private Integer creditPeriodDays;

    @Column(name = "discount_hold", nullable = false, length = 3)
    @Builder.Default
    private String discountHold = "NO";

    @Column(name = "payment_method", nullable = false, length = 30)
    @Builder.Default
    private String paymentMethod = "SMART_COLLECT";

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method_mode", nullable = false, length = 10)
    @Builder.Default
    private PaymentMethodMode paymentMethodMode = PaymentMethodMode.CUSTOM;

    @Column(name = "overdue_interest_rate", precision = 7, scale = 4, nullable = false)
    @Builder.Default
    private BigDecimal overdueInterestRate = BigDecimal.ZERO;

    /** Anchor-specific counterparty code for CSV invoice upload (unique per sub-program). */
    @Column(name = "party_code", length = 50)
    private String partyCode;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "ACTIVE";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
