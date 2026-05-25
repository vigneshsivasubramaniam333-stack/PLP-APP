package com.plp.program.model.entity;

import com.plp.program.model.enums.AnchorStatus;
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
@Table(name = "anchors", schema = "plp_program")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Anchor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 20)
    private String anchorCode;

    @Column(nullable = false, length = 200)
    private String entityName;

    @Column(nullable = false, length = 30)
    private String entityType;

    @Column(length = 15)
    private String gstin;

    @Column(length = 10)
    private String pan;

    @Column(length = 21)
    private String cin;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> address;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> bankAccount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> integrationConfig;

    @Column(length = 100)
    private String contactPersonName;

    @Column(length = 100)
    private String contactEmail;

    @Column(length = 15)
    private String contactPhone;

    private UUID agreementDocId;

    @Column(length = 5)
    private String rating;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AnchorStatus status = AnchorStatus.DRAFT;

    /** External LOS tenant / product line (partial unique with {@link #losAnchorId} in DB). */
    @Column(name = "source_system", length = 50)
    private String sourceSystem;

    /** Anchor identifier in the originating LOS. */
    @Column(name = "los_anchor_id", length = 100)
    private String losAnchorId;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
