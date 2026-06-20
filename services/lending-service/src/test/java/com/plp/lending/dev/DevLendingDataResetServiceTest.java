package com.plp.lending.dev;

import com.plp.lending.audit.AuditEventRepository;
import com.plp.lending.repository.DisbursementRepository;
import com.plp.lending.repository.LmsLoanOperationRepository;
import com.plp.lending.repository.LoanRepository;
import com.plp.lending.repository.RepaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class DevLendingDataResetServiceTest {

    @Mock
    private LmsLoanOperationRepository lmsLoanOperationRepository;
    @Mock
    private RepaymentRepository repaymentRepository;
    @Mock
    private DisbursementRepository disbursementRepository;
    @Mock
    private LoanRepository loanRepository;
    @Mock
    private AuditEventRepository auditEventRepository;

    private DevLendingDataResetService service;

    @BeforeEach
    void setUp() {
        service = new DevLendingDataResetService(
                lmsLoanOperationRepository,
                repaymentRepository,
                disbursementRepository,
                loanRepository,
                auditEventRepository);
    }

    @Test
    void resetAllLendingData_deletesInFkSafeOrder() {
        InOrder order = inOrder(
                lmsLoanOperationRepository,
                repaymentRepository,
                disbursementRepository,
                loanRepository,
                auditEventRepository);
        service.resetAllLendingData();
        order.verify(lmsLoanOperationRepository).deleteAllInBatch();
        order.verify(repaymentRepository).deleteAllInBatch();
        order.verify(disbursementRepository).deleteAllInBatch();
        order.verify(loanRepository).deleteAllInBatch();
        order.verify(auditEventRepository).deleteAllInBatch();
    }
}
