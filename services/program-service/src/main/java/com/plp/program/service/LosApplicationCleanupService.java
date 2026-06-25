package com.plp.program.service;

import com.plp.program.integration.LendingServiceBorrowerCleanupClient;
import com.plp.program.integration.iam.IamBorrowerCleanupClient;
import com.plp.program.model.dto.integration.LosApplicationCleanupRequest;
import com.plp.program.model.dto.integration.LosApplicationCleanupResponse;
import com.plp.program.model.entity.Borrower;
import com.plp.program.model.entity.BorrowerProgramMapping;
import com.plp.program.model.entity.SubProgramBorrower;
import com.plp.program.repository.BorrowerLimitRepository;
import com.plp.program.repository.BorrowerProgramMappingRepository;
import com.plp.program.repository.BorrowerRepository;
import com.plp.program.repository.InvoiceRepository;
import com.plp.program.repository.SubProgramBorrowerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LosApplicationCleanupService {

    private final LendingServiceBorrowerCleanupClient lendingCleanupClient;
    private final IamBorrowerCleanupClient iamBorrowerCleanupClient;
    private final InvoiceRepository invoiceRepository;
    private final BorrowerProgramMappingRepository borrowerProgramMappingRepository;
    private final SubProgramBorrowerRepository subProgramBorrowerRepository;
    private final BorrowerLimitRepository borrowerLimitRepository;
    private final BorrowerRepository borrowerRepository;

    @Transactional(rollbackFor = Exception.class)
    public LosApplicationCleanupResponse cleanup(LosApplicationCleanupRequest request) {
        UUID borrowerId = request.getPlpBorrowerId();
        int loansRemoved = 0;
        int invoicesRemoved = 0;
        boolean borrowerRemoved = false;

        if (borrowerId != null) {
            loansRemoved = lendingCleanupClient.cleanupBorrowerLoans(borrowerId);
            List<com.plp.program.model.entity.Invoice> invoices = invoiceRepository.findByBorrowerId(borrowerId);
            invoicesRemoved = invoices.size();
            if (!invoices.isEmpty()) {
                invoiceRepository.deleteAllInBatch(invoices);
            }
            borrowerLimitRepository.findByBorrowerId(borrowerId).forEach(borrowerLimitRepository::delete);
        }

        if (request.getPlpBorrowerProgramMappingId() != null) {
            borrowerProgramMappingRepository.findById(request.getPlpBorrowerProgramMappingId())
                    .ifPresent(borrowerProgramMappingRepository::delete);
        } else if (request.getLosApplicationId() != null && !request.getLosApplicationId().isBlank()) {
            Optional<BorrowerProgramMapping> byApp = borrowerProgramMappingRepository
                    .findBySourceSystemAndLosApplicationId(
                            normalizeSource(request.getSourceSystem()), request.getLosApplicationId().trim());
            byApp.ifPresent(borrowerProgramMappingRepository::delete);
        }

        if (request.getPlpSubProgramBorrowerId() != null) {
            subProgramBorrowerRepository.findById(request.getPlpSubProgramBorrowerId())
                    .ifPresent(subProgramBorrowerRepository::delete);
        } else if (borrowerId != null) {
            List<SubProgramBorrower> links = subProgramBorrowerRepository.findByBorrowerId(borrowerId);
            if (!links.isEmpty()) {
                subProgramBorrowerRepository.deleteAllInBatch(links);
            }
        }

        if (request.isDeleteBorrowerRecord() && borrowerId != null) {
            Optional<Borrower> borrower = borrowerRepository.findById(borrowerId);
            if (borrower.isPresent()) {
                borrowerRepository.delete(borrower.get());
                borrowerRemoved = true;
                iamBorrowerCleanupClient.deleteBorrowerPortalUser(borrowerId);
            }
        }

        String summary = "Removed "
                + loansRemoved + " loan(s), "
                + invoicesRemoved + " invoice(s)"
                + (borrowerRemoved ? ", borrower record" : "")
                + ".";
        log.info("LOS application cleanup for {}: {}", request.getLosApplicationId(), summary);
        return LosApplicationCleanupResponse.builder()
                .summary(summary)
                .loansRemoved(loansRemoved)
                .invoicesRemoved(invoicesRemoved)
                .borrowerRemoved(borrowerRemoved)
                .build();
    }

    private static String normalizeSource(String sourceSystem) {
        return sourceSystem == null || sourceSystem.isBlank() ? "LOS" : sourceSystem.trim();
    }
}
