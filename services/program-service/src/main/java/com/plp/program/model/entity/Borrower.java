package com.plp.program.model.entity;

import com.plp.program.model.enums.BorrowerStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "borrowers", schema = "plp_program")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Borrower {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 20)
    private String borrowerCode;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false)
    private UUID programId;

    @Column(nullable = false)
    private UUID anchorId;

    @Column(length = 100)
    private String email;

    @Column(length = 15)
    private String phone;

    @Column(length = 10)
    private String pan;

    @Column(length = 12)
    private String aadhaarHash;

    @Column(length = 15)
    private String gstin;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> personalInfo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> bankAccount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> employmentInfo;

    @Column(length = 30)
    private String kycStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private BorrowerStatus status = BorrowerStatus.PENDING_KYC;

    /** External LOS tenant / product line for stable borrower correlation. */
    @Column(name = "source_system", length = 50)
    private String sourceSystem;

    /** Borrower identifier in the originating LOS. */
    @Column(name = "los_borrower_id", length = 100)
    private String losBorrowerId;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
