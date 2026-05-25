package com.plp.program.model.entity;

import com.plp.program.model.enums.BorrowerProgramMappingStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
        name = "borrower_program_mappings",
        schema = "plp_program",
        uniqueConstraints =
                @UniqueConstraint(name = "uk_bpm_source_system_los_application", columnNames = {"source_system", "los_application_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BorrowerProgramMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "source_system", nullable = false, length = 50)
    private String sourceSystem;

    @Column(name = "los_application_id", nullable = false, length = 100)
    private String losApplicationId;

    @Column(name = "los_borrower_id", nullable = false, length = 100)
    private String losBorrowerId;

    @Column(name = "borrower_id", nullable = false)
    private UUID borrowerId;

    @Column(name = "program_id", nullable = false)
    private UUID programId;

    @Column(name = "sub_program_id", nullable = false)
    private UUID subProgramId;

    @Column(name = "approved_limit", nullable = false, precision = 19, scale = 2)
    private BigDecimal approvedLimit;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to", nullable = false)
    private LocalDate validTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private BorrowerProgramMappingStatus status = BorrowerProgramMappingStatus.PENDING_APPROVAL;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
