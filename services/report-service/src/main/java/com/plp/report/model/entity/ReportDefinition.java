package com.plp.report.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "report_definitions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReportDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "report_code", nullable = false, unique = true)
    private String reportCode;

    @Column(name = "report_name", nullable = false)
    private String reportName;

    private String category;

    @Column(name = "query_template", columnDefinition = "TEXT")
    private String queryTemplate;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> parameters;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }
}
