package com.plp.report.model.entity;

import com.plp.report.model.enums.ReportStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "generated_reports")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GeneratedReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_definition_id")
    private ReportDefinition reportDefinition;

    @Column(name = "requested_by")
    private UUID requestedBy;

    @Column(name = "parameters_used", columnDefinition = "jsonb")
    private String parametersUsed;

    @Column(name = "file_path")
    private String filePath;

    @Builder.Default
    private String format = "CSV";

    @Column(name = "row_count")
    private Integer rowCount;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ReportStatus status = ReportStatus.QUEUED;

    @Column(name = "generated_at")
    private OffsetDateTime generatedAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}
