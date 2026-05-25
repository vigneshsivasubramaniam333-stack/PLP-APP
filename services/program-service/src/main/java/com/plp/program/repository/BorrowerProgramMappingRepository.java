package com.plp.program.repository;

import com.plp.program.model.entity.BorrowerProgramMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BorrowerProgramMappingRepository extends JpaRepository<BorrowerProgramMapping, UUID> {

    Optional<BorrowerProgramMapping> findBySourceSystemAndLosApplicationId(
            String sourceSystem, String losApplicationId);
}
