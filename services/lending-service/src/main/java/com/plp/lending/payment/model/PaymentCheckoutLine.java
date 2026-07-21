package com.plp.lending.payment.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_checkout_lines", schema = "plp_lending")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentCheckoutLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "borrower_id", nullable = false)
    private UUID borrowerId;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "loan_id")
    private UUID loanId;

    @Column(name = "sub_program_id")
    private UUID subProgramId;

    @Column(name = "program_id", nullable = false)
    private UUID programId;

    @Column(name = "invoice_number", length = 50)
    private String invoiceNumber;

    @Column(name = "amount_to_pay", nullable = false, precision = 15, scale = 2)
    private BigDecimal amountToPay;

    /**
     * LMS interest due (e.g. totalNormalInterestDue) for display only — not persisted on cart lines.
     */
    @Transient
    private BigDecimal interestAmount;

    @Column(name = "discount_amount", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "INITIALIZED";

    @Column(name = "pg_transaction_id")
    private UUID pgTransactionId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
