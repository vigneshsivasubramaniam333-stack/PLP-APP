package com.plp.lending.repository;

import com.plp.lending.model.entity.LmsLoanOperation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LmsLoanOperationRepository extends JpaRepository<LmsLoanOperation, UUID> {

    List<LmsLoanOperation> findByLoanIdOrderByCreatedAtDesc(UUID loanId);

    Optional<LmsLoanOperation> findFirstByLoanIdAndOperationAndStatusOrderByCreatedAtDesc(
            UUID loanId, String operation, String status);
}
