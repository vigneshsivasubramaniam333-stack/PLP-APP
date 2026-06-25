package com.plp.lending.payment.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_in_progress", schema = "plp_lending")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentInProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "pg_transaction_id", nullable = false)
    private UUID pgTransactionId;

    @Column(name = "checkout_line_id")
    private UUID checkoutLineId;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "loan_id", nullable = false)
    private UUID loanId;

    @Column(name = "borrower_id", nullable = false)
    private UUID borrowerId;

    @Column(name = "principal_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal principalAmount;

    @Column(name = "discount_amount", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "pip_status", nullable = false, length = 20)
    @Builder.Default
    private String pipStatus = "OPEN";

    @Column(name = "settlement_batch_id")
    private UUID settlementBatchId;

    @Column(name = "settled_at")
    private Instant settledAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
