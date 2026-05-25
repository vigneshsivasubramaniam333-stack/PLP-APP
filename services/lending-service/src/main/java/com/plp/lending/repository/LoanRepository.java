package com.plp.lending.repository;

import com.plp.lending.model.entity.Loan;
import com.plp.lending.model.enums.LoanStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LoanRepository extends JpaRepository<Loan, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM Loan l WHERE l.id = :id")
    Optional<Loan> findByIdForUpdate(UUID id);

    Optional<Loan> findByLoanNumber(String loanNumber);

    List<Loan> findByBorrowerId(UUID borrowerId);

    List<Loan> findByAnchorId(UUID anchorId);

    List<Loan> findByProgramId(UUID programId);

    List<Loan> findByBorrowerIdAndStatus(UUID borrowerId, LoanStatus status);

    List<Loan> findByProgramIdAndStatus(UUID programId, LoanStatus status);

    List<Loan> findByStatus(LoanStatus status);

    boolean existsByInvoiceIdAndStatusIn(UUID invoiceId, List<LoanStatus> statuses);

    boolean existsBySalaryDataIdAndStatusIn(UUID salaryDataId, Collection<LoanStatus> statuses);

    @Query("SELECT COUNT(l) FROM Loan l WHERE l.borrowerId = :borrowerId AND l.status IN ('DISBURSED', 'REPAYMENT_DUE', 'OVERDUE')")
    int countActiveLoansByBorrower(UUID borrowerId);

    @Query("SELECT l FROM Loan l WHERE l.status = 'DISBURSED' AND l.dueDate < CURRENT_DATE")
    List<Loan> findOverdueLoans();

    @Query(
            """
            SELECT l FROM Loan l
            WHERE l.subProgramId IS NULL
            AND l.status NOT IN ('CLOSED', 'CANCELLED', 'REJECTED')
            AND l.productType IN ('PAY_DAY_LOAN', 'INVOICE_DISCOUNTING')
            """)
    List<Loan> findOpenLoansMissingSubProgramLink();
}
