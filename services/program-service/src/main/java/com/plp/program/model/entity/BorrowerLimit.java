package com.plp.program.model.entity;

import com.plp.program.model.enums.LimitStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "borrower_limits", schema = "plp_program")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BorrowerLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID borrowerId;

    @Column(nullable = false)
    private UUID programId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal sanctionedLimit;

    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal utilizedLimit = BigDecimal.ZERO;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal availableLimit;

    @Column(precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal overdueAmount = BigDecimal.ZERO;

    @Column(precision = 5, scale = 2)
    private BigDecimal interestRate;

    @Column
    @Builder.Default
    private Integer maxConcurrentLoans = 1;

    @Column
    @Builder.Default
    private Integer activeLoanCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private LimitStatus status = LimitStatus.ACTIVE;

    private Instant lastEvaluatedAt;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    @jakarta.persistence.Version
    @Builder.Default
    private Long version = 0L;
}
