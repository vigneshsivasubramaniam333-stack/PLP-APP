package com.plp.program.repository;

import com.plp.program.model.entity.LosSyncAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface LosSyncAuditRepository extends JpaRepository<LosSyncAudit, UUID> {}
