package com.plp.program.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "early_pay_requests", schema = "plp_program")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EarlyPayRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "ep_parameter_id", nullable = false)
    private UUID epParameterId;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "borrower_id", nullable = false)
    private UUID borrowerId;

    @Column(name = "sub_program_id", nullable = false)
    private UUID subProgramId;

    @Column(name = "invoice_no", nullable = false, length = 50)
    private String invoiceNo;

    @Column(name = "invoice_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal invoiceAmount;

    @Column(name = "requested_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal requestedAmount;

    @Column(name = "cd_amount", precision = 15, scale = 2)
    private BigDecimal cdAmount;

    @Column(name = "cd_percentage", precision = 7, scale = 4)
    private BigDecimal cdPercentage;

    @Column(name = "ep_date", nullable = false)
    private LocalDate epDate;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "REQUESTED";

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
