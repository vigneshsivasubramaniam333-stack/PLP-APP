package com.plp.lending.repository;

import com.plp.lending.model.entity.Disbursement;
import com.plp.lending.model.enums.DisbursementStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DisbursementRepository extends JpaRepository<Disbursement, UUID> {

    Optional<Disbursement> findByDisbursementRef(String disbursementRef);

    List<Disbursement> findByLoanId(UUID loanId);

    List<Disbursement> findByStatus(DisbursementStatus status);
}
