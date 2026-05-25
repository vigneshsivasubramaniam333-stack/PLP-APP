package com.plp.program.repository;

import com.plp.program.model.entity.Borrower;
import com.plp.program.model.enums.BorrowerStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BorrowerRepository extends JpaRepository<Borrower, UUID> {

    Optional<Borrower> findByBorrowerCode(String borrowerCode);

    List<Borrower> findByProgramId(UUID programId);

    List<Borrower> findByAnchorId(UUID anchorId);

    List<Borrower> findByProgramIdAndStatus(UUID programId, BorrowerStatus status);

    Optional<Borrower> findByPanAndProgramId(String pan, UUID programId);

    Optional<Borrower> findBySourceSystemAndLosBorrowerId(String sourceSystem, String losBorrowerId);
}
