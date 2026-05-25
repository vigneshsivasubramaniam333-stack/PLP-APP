package com.plp.report.repository;

import com.plp.report.model.entity.GeneratedReport;
import com.plp.report.model.enums.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface GeneratedReportRepository extends JpaRepository<GeneratedReport, UUID> {
    Page<GeneratedReport> findByRequestedByOrderByCreatedAtDesc(UUID requestedBy, Pageable pageable);
    List<GeneratedReport> findByStatus(ReportStatus status);
}
