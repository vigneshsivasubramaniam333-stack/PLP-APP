package com.plp.program.repository;

import com.plp.program.model.entity.SubProgram;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubProgramRepository extends JpaRepository<SubProgram, UUID> {

    List<SubProgram> findByProgramId(UUID programId);

    List<SubProgram> findByAnchorId(UUID anchorId);

    boolean existsByProgramIdAndAnchorId(UUID programId, UUID anchorId);

    boolean existsByCode(String code);

    Optional<SubProgram> findByCode(String code);

    Optional<SubProgram> findBySourceSystemAndLosSubProgramId(String sourceSystem, String losSubProgramId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SubProgram s WHERE s.id = :id")
    Optional<SubProgram> findByIdForUpdate(@Param("id") UUID id);
}
