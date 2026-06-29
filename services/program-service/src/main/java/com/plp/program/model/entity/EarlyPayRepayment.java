package com.plp.program.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Ledger of SBD Early Pay repayments. One row per repayment recorded against an
 * approved Early Pay request (amount = the EP payout / requested amount).
 */
@Entity
@Table(name = "early_pay_repayments", schema = "plp_program")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EarlyPayRepayment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "ep_request_id", nullable = false)
    private UUID epRequestId;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "borrower_id", nullable = false)
    private UUID borrowerId;

    @Column(name = "anchor_id", nullable = false)
    private UUID anchorId;

    @Column(name = "sub_program_id", nullable = false)
    private UUID subProgramId;

    @Column(name = "invoice_no", nullable = false, length = 50)
    private String invoiceNo;

    /** Repayment amount = the Early Pay payout (approved request requestedAmount). */
    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    @CreationTimestamp
    @Column(name = "repaid_at", updatable = false)
    private Instant repaidAt;
}
