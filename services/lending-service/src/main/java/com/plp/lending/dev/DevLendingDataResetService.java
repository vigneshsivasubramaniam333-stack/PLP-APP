package com.plp.lending.dev;

import com.plp.lending.audit.AuditEventRepository;
import com.plp.lending.repository.DisbursementRepository;
import com.plp.lending.repository.LmsLoanOperationRepository;
import com.plp.lending.repository.LoanRepository;
import com.plp.lending.repository.RepaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DevLendingDataResetService {

    private final LmsLoanOperationRepository lmsLoanOperationRepository;
    private final RepaymentRepository repaymentRepository;
    private final DisbursementRepository disbursementRepository;
    private final LoanRepository loanRepository;
    private final AuditEventRepository auditEventRepository;

    @Transactional
    public void resetAllLendingData() {
        lmsLoanOperationRepository.deleteAllInBatch();
        repaymentRepository.deleteAllInBatch();
        disbursementRepository.deleteAllInBatch();
        loanRepository.deleteAllInBatch();
        auditEventRepository.deleteAllInBatch();
    }
}
