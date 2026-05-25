package com.plp.report.repository;

import com.plp.report.model.entity.AuditTrail;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AuditTrailRepository extends JpaRepository<AuditTrail, UUID> {
    Page<AuditTrail> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, UUID entityId, Pageable pageable);
    Page<AuditTrail> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<AuditTrail> findByActorIdOrderByCreatedAtDesc(UUID actorId);
}
