package com.plp.program.repository;

import com.plp.program.model.entity.SubProgramBorrower;
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
public interface SubProgramBorrowerRepository extends JpaRepository<SubProgramBorrower, UUID> {

    List<SubProgramBorrower> findBySubProgramId(UUID subProgramId);

    List<SubProgramBorrower> findByBorrowerId(UUID borrowerId);

    Optional<SubProgramBorrower> findBySubProgramIdAndBorrowerId(UUID subProgramId, UUID borrowerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM SubProgramBorrower m WHERE m.subProgramId = :subProgramId AND m.borrowerId = :borrowerId")
    Optional<SubProgramBorrower> findBySubProgramIdAndBorrowerIdForUpdate(
            @Param("subProgramId") UUID subProgramId,
            @Param("borrowerId") UUID borrowerId);
}
