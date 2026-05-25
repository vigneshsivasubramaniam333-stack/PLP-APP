package com.plp.program.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sub_programs", schema = "plp_program")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubProgram {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "program_id", nullable = false)
    private UUID programId;

    @Column(name = "anchor_id", nullable = false)
    private UUID anchorId;

    @Column(name = "lender_id")
    private UUID lenderId;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "flow_type", nullable = false, length = 40)
    private String flowType;

    @Column(name = "anchor_role", nullable = false, length = 20)
    private String anchorRole;

    @Column(name = "borrower_role", nullable = false, length = 20)
    private String borrowerRole;

    @Column(name = "sub_program_limit", precision = 19, scale = 2)
    private BigDecimal subProgramLimit;

    @Column(name = "utilized_limit", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal utilizedLimit = BigDecimal.ZERO;

    @Column(name = "available_limit", precision = 19, scale = 2)
    private BigDecimal availableLimit;

    @Column(name = "interest_rate", precision = 8, scale = 4)
    private BigDecimal interestRate;

    @Column(name = "margin_percent", precision = 8, scale = 4)
    private BigDecimal marginPercent;

    @Column(name = "max_tenure_days")
    private Integer maxTenureDays;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "DRAFT";

    @Column(name = "source_system", length = 50)
    private String sourceSystem;

    @Column(name = "los_sub_program_id", length = 100)
    private String losSubProgramId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
