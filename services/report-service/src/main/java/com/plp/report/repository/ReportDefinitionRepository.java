package com.plp.report.repository;

import com.plp.report.model.entity.ReportDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ReportDefinitionRepository extends JpaRepository<ReportDefinition, UUID> {
    Optional<ReportDefinition> findByReportCode(String reportCode);
}
