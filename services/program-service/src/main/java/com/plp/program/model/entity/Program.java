package com.plp.program.model.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.plp.program.model.enums.ProductType;
import com.plp.program.model.enums.ProgramStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "programs", schema = "plp_program")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Program {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 30)
    private String programCode;

    @Column(nullable = false, length = 200)
    private String programName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProductType productType;

    /** Optional for umbrella programs — anchor association is on sub-programs. */
    private UUID anchorId;

    @Column(nullable = false)
    private UUID lenderId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal programLimit;

    /** Legacy / optional; prefer limits on sub-programs. */
    @Column(precision = 15, scale = 2)
    private BigDecimal anchorLimit;

    @Transient
    @JsonProperty("utilizedLimit")
    private BigDecimal utilizedLimit;

    /** programLimit - utilized (from program-level borrower limits); not persisted. */
    @Transient
    @JsonProperty("availableLimit")
    private BigDecimal availableLimit;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal maxBorrowerLimit;

    @Column(precision = 5, scale = 2)
    private BigDecimal interestRateMin;

    @Column(precision = 5, scale = 2)
    private BigDecimal interestRateMax;

    @Column(precision = 5, scale = 2)
    private BigDecimal defaultInterestRate;

    @Column
    private Integer maxTenureDays;

    @Column(precision = 5, scale = 2)
    private BigDecimal marginPercent;

    @Column(precision = 5, scale = 2)
    private BigDecimal processingFeePercent;

    @Column(precision = 5, scale = 2)
    private BigDecimal penalRatePercent;

    @Column
    private Integer gracePeriodDays;

    @Column(precision = 15, scale = 2)
    private BigDecimal autoApproveThreshold;

    @Column
    private Integer maxConcurrentLoans;

    @Column
    private Integer coolingOffDays;

    @Column
    private Integer eligibilityRefreshDays;

    @Column(precision = 5, scale = 2)
    private BigDecimal concentrationLimitPercent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> eligibilityRules;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", columnDefinition = "jsonb")
    private Map<String, Object> config;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> parameters;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ProgramStatus status = ProgramStatus.DRAFT;

    private LocalDate validFrom;

    private LocalDate validTo;

    @Builder.Default
    private boolean autoRenewal = false;

    @Column(name = "source_system", length = 50)
    private String sourceSystem;

    @Column(name = "los_program_id", length = 100)
    private String losProgramId;

    @Column(name = "lms_entry_in", nullable = false, length = 3)
    @Builder.Default
    private String lmsEntryIn = "NO";

    @Column(name = "encore_product_code", length = 50)
    private String encoreProductCode;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
