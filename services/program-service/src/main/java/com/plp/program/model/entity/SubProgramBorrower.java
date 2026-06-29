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

    @Column(name = "borrower_od_account_number", length = 50)
    private String borrowerOdAccountNumber;

    @Column(name = "borrower_od_bank_name", length = 120)
    private String borrowerOdBankName;

    @Column(name = "borrower_od_bank_ifsc", length = 20)
    private String borrowerOdBankIfsc;

    @Column(name = "borrower_od_account_name", length = 120)
    private String borrowerOdAccountName;

    @Column(name = "idfc_collection_account_name", length = 120)
    private String idfcCollectionAccountName;

    @Column(name = "idfc_od_account_number", length = 50)
    private String idfcOdAccountNumber;

    @Column(name = "idfc_ifsc_code", length = 20)
    private String idfcIfscCode;

    @Column(name = "idfc_upi_id", length = 100)
    private String idfcUpiId;

    /** ESCROW_ICICI | ESCROW_CASTLER | NOT_REQUIRED */
    @Column(name = "castler_escrow_account_in", length = 30)
    private String castlerEscrowAccountIn;

    @Column(name = "castler_escrow_account_id", length = 80)
    private String castlerEscrowAccountId;

    @Column(name = "castler_escrow_payee_id", length = 80)
    private String castlerEscrowPayeeId;

    @Column(name = "razorpay_route_account_id", length = 80)
    private String razorpayRouteAccountId;

    @Column(name = "razorpay_smart_collect_ac_id", length = 80)
    private String razorpaySmartCollectAcId;

    @Column(name = "razorpay_fee", precision = 19, scale = 4)
    private BigDecimal razorpayFee;

    @Column(name = "hdfc_account_no", length = 50)
    private String hdfcAccountNo;

    @Column(name = "hdfc_ifsc_code", length = 20)
    private String hdfcIfscCode;

    /** YES/NO — borrower may request Early Pay on this SBD sub-program (legacy borrower_debtor_link). */
    @Column(name = "enable_early_pay", nullable = false, length = 3)
    @Builder.Default
    private String enableEarlyPay = "NO";

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
