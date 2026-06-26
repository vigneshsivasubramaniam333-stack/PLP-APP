package com.plp.lending.service.integration;

import com.plp.lending.model.entity.Loan;
import com.plp.lending.repository.DisbursementRepository;
import com.plp.lending.repository.LmsLoanOperationRepository;
import com.plp.lending.repository.LoanRepository;
import com.plp.lending.repository.RepaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LosBorrowerLendingCleanupService {

    private final LoanRepository loanRepository;
    private final RepaymentRepository repaymentRepository;
    private final DisbursementRepository disbursementRepository;
    private final LmsLoanOperationRepository lmsLoanOperationRepository;

    @Transactional(rollbackFor = Exception.class)
    public int cleanupBorrower(UUID borrowerId) {
        List<Loan> loans = loanRepository.findByBorrowerId(borrowerId);
        for (Loan loan : loans) {
            repaymentRepository.findByLoanId(loan.getId()).forEach(repaymentRepository::delete);
            disbursementRepository.findByLoanId(loan.getId()).forEach(disbursementRepository::delete);
            lmsLoanOperationRepository.findByLoanIdOrderByCreatedAtDesc(loan.getId())
                    .forEach(lmsLoanOperationRepository::delete);
            loanRepository.delete(loan);
        }
        return loans.size();
    }
}
