package com.plp.program.model.entity;

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
