package com.plp.program.repository;

import com.plp.program.model.entity.ProgramFieldDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProgramFieldDefinitionRepository extends JpaRepository<ProgramFieldDefinition, UUID> {

    Optional<ProgramFieldDefinition> findByFieldKey(String fieldKey);

    boolean existsByFieldKey(String fieldKey);

    List<ProgramFieldDefinition> findAllByOrderBySortOrderAscLabelAsc();

    List<ProgramFieldDefinition> findByActiveTrueOrderBySortOrderAscLabelAsc();
}
