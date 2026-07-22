package com.plp.program.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface EntityRecordAuditRepository extends JpaRepository<EntityRecordAudit, UUID> {
}
