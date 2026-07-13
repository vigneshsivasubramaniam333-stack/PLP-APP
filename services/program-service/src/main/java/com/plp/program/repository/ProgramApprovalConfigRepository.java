package com.plp.program.repository;

import com.plp.program.model.entity.ProgramApprovalConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProgramApprovalConfigRepository extends JpaRepository<ProgramApprovalConfig, UUID> {}
