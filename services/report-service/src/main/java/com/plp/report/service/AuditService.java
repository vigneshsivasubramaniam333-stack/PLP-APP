package com.plp.report.service;

import com.plp.report.model.entity.AuditTrail;
import com.plp.report.repository.AuditTrailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditTrailRepository auditTrailRepository;

    @Transactional
    public AuditTrail recordAudit(String entityType, UUID entityId, String action,
                                   UUID actorId, String actorRole,
                                   String oldValues, String newValues, String metadata) {
        AuditTrail audit = AuditTrail.builder()
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .actorId(actorId)
                .actorRole(actorRole)
                .oldValues(oldValues)
                .newValues(newValues)
                .metadata(metadata)
                .build();
        audit = auditTrailRepository.save(audit);
        log.debug("Audit recorded: {} {} {} by {}", entityType, entityId, action, actorId);
        return audit;
    }

    public Page<AuditTrail> getAuditTrail(Pageable pageable) {
        return auditTrailRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    public Page<AuditTrail> getEntityAuditTrail(String entityType, UUID entityId, Pageable pageable) {
        return auditTrailRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId, pageable);
    }
}
