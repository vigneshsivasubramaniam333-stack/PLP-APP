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
@Table(name = "early_pay_parameters", schema = "plp_program",
        uniqueConstraints = @UniqueConstraint(columnNames = {"sub_program_id", "ep_date"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EarlyPayParameter {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "sub_program_id", nullable = false)
    private UUID subProgramId;

    @Column(name = "anchor_id", nullable = false)
    private UUID anchorId;

    @Column(name = "ep_date", nullable = false)
    private LocalDate epDate;

    @Column(name = "discount_percentage", nullable = false, precision = 7, scale = 4)
    private BigDecimal discountPercentage;

    @Column(name = "ep_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal epAmount;

    @Column(name = "total_margin_amount", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal totalMarginAmount = BigDecimal.ZERO;

    @Column(name = "consumed_margin_amount", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal consumedMarginAmount = BigDecimal.ZERO;

    @Column(name = "un_allocated_amount", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal unAllocatedAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
