package com.plp.lending.repository;

import com.plp.lending.model.entity.Repayment;
import com.plp.lending.model.enums.RepaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RepaymentRepository extends JpaRepository<Repayment, UUID> {

    Optional<Repayment> findByRepaymentRef(String repaymentRef);

    List<Repayment> findByLoanId(UUID loanId);

    List<Repayment> findByLoanIdAndStatus(UUID loanId, RepaymentStatus status);
}
