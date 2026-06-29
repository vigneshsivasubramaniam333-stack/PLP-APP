package com.plp.program.dev;

import com.plp.program.audit.AuditEventRepository;
import com.plp.program.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes business/demo data in FK-safe order (program schema).
 */
@Service
@RequiredArgsConstructor
public class DevProgramDataResetService {

    private final EarlyPayRepaymentRepository earlyPayRepaymentRepository;
    private final EarlyPayRequestRepository earlyPayRequestRepository;
    private final EarlyPayParameterRepository earlyPayParameterRepository;
    private final InvoiceRepository invoiceRepository;
    private final EmployeeSalaryDataRepository employeeSalaryDataRepository;
    private final BorrowerProgramMappingRepository borrowerProgramMappingRepository;
    private final LosSyncAuditRepository losSyncAuditRepository;
    private final SubProgramBorrowerRepository subProgramBorrowerRepository;
    private final SubProgramRepository subProgramRepository;
    private final BorrowerLimitRepository borrowerLimitRepository;
    private final BorrowerRepository borrowerRepository;
    private final ProgramRepository programRepository;
    private final AnchorRepository anchorRepository;
    private final AuditEventRepository auditEventRepository;

    @Transactional
    public void resetAllProgramData() {
        // Early-pay rows reference invoices/sub-programs/anchors — delete them first (FK-safe).
        earlyPayRepaymentRepository.deleteAllInBatch();
        earlyPayRequestRepository.deleteAllInBatch();
        earlyPayParameterRepository.deleteAllInBatch();
        invoiceRepository.deleteAllInBatch();
        employeeSalaryDataRepository.deleteAllInBatch();
        losSyncAuditRepository.deleteAllInBatch();
        borrowerProgramMappingRepository.deleteAllInBatch();
        subProgramBorrowerRepository.deleteAllInBatch();
        subProgramRepository.deleteAllInBatch();
        borrowerLimitRepository.deleteAllInBatch();
        borrowerRepository.deleteAllInBatch();
        programRepository.deleteAllInBatch();
        anchorRepository.deleteAllInBatch();
        auditEventRepository.deleteAllInBatch();
    }
}
