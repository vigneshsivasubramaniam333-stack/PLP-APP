package com.plp.program.repository;

import com.plp.program.model.entity.BorrowerLimit;
import com.plp.program.model.enums.LimitStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BorrowerLimitRepository extends JpaRepository<BorrowerLimit, UUID> {

    Optional<BorrowerLimit> findByBorrowerIdAndProgramId(UUID borrowerId, UUID programId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT bl FROM BorrowerLimit bl WHERE bl.borrowerId = :borrowerId AND bl.programId = :programId")
    Optional<BorrowerLimit> findByBorrowerIdAndProgramIdForUpdate(UUID borrowerId, UUID programId);

    List<BorrowerLimit> findByBorrowerId(UUID borrowerId);

    List<BorrowerLimit> findByProgramId(UUID programId);

    List<BorrowerLimit> findByProgramIdAndStatus(UUID programId, LimitStatus status);

    @Query("SELECT COALESCE(SUM(bl.utilizedLimit), 0) FROM BorrowerLimit bl WHERE bl.programId = :programId")
    BigDecimal sumUtilizedByProgramId(UUID programId);
}
