package com.plp.lending.model.entity;

import com.plp.lending.model.enums.RepaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "repayments", schema = "plp_lending")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Repayment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID loanId;

    @Column(nullable = false, unique = true, length = 30)
    private String repaymentRef;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal expectedAmount;

    @Column(precision = 15, scale = 2)
    private BigDecimal paidAmount;

    @Column(precision = 15, scale = 2)
    private BigDecimal principalComponent;

    @Column(precision = 15, scale = 2)
    private BigDecimal interestComponent;

    @Column(precision = 15, scale = 2)
    private BigDecimal penalComponent;

    private LocalDate dueDate;

    private LocalDate paidDate;

    @Column(length = 50)
    private String paymentMode;

    @Column(length = 100)
    private String utrNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private RepaymentStatus status = RepaymentStatus.SCHEDULED;

    @Column(length = 500)
    private String failureReason;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
