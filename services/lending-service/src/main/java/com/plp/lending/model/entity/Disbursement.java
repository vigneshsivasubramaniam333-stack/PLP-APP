package com.plp.lending.model.entity;

import com.plp.lending.model.enums.DisbursementStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "disbursements", schema = "plp_lending")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Disbursement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID loanId;

    @Column(nullable = false, unique = true, length = 30)
    private String disbursementRef;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(length = 50)
    private String paymentMode;

    @Column(length = 100)
    private String utrNumber;

    @Column(length = 50)
    private String beneficiaryAccount;

    @Column(length = 30)
    private String beneficiaryIfsc;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private DisbursementStatus status = DisbursementStatus.INITIATED;

    private Instant initiatedAt;

    private Instant completedAt;

    @Column(length = 500)
    private String failureReason;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
