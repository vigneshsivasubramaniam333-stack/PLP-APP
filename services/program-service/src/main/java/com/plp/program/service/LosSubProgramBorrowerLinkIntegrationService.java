package com.plp.program.service;

import com.plp.program.integration.los.LosIntegrationResourceTypes;
import com.plp.program.model.dto.integration.LosSubProgramBorrowerLinkRequest;
import com.plp.program.model.dto.integration.LosSubProgramBorrowerLinkResponse;
import com.plp.program.model.entity.SubProgramBorrower;
import com.plp.program.repository.BorrowerRepository;
import com.plp.program.repository.SubProgramBorrowerRepository;
import com.plp.program.repository.SubProgramRepository;
import com.plp.program.service.audit.LosSyncAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LosSubProgramBorrowerLinkIntegrationService {

    private static final String STATUS_PENDING = "PENDING_APPROVAL";

    private final SubProgramBorrowerRepository subProgramBorrowerRepository;
    private final SubProgramRepository subProgramRepository;
    private final BorrowerRepository borrowerRepository;
    private final LosSyncAuditService losSyncAuditService;

    @Transactional
    public LosSubProgramBorrowerLinkResponse link(LosSubProgramBorrowerLinkRequest req) {
        String sourceSystem = normalize(req.getSourceSystem());
        String extKey =
                "subProgram:" + req.getSubProgramId() + ":borrower:" + req.getBorrowerId();
        try {
            LosSubProgramBorrowerLinkResponse response = linkInternal(req);
            losSyncAuditService.recordSuccess(
                    LosIntegrationResourceTypes.SUB_PROGRAM_BORROWER,
                    response.getPlpSubProgramBorrowerId(),
                    sourceSystem,
                    extKey,
                    req,
                    response);
            return response;
        } catch (RuntimeException e) {
            losSyncAuditService.recordFailure(
                    LosIntegrationResourceTypes.SUB_PROGRAM_BORROWER,
                    null,
                    sourceSystem,
                    extKey,
                    req,
                    e.getMessage());
            throw e;
        }
    }

    private LosSubProgramBorrowerLinkResponse linkInternal(LosSubProgramBorrowerLinkRequest req) {
        UUID subProgramId = req.getSubProgramId();
        UUID borrowerId = req.getBorrowerId();

        subProgramRepository.findById(subProgramId).orElseThrow(() -> new RuntimeException("Sub-program not found"));
        borrowerRepository.findById(borrowerId).orElseThrow(() -> new RuntimeException("Borrower not found"));

        BigDecimal limit = req.getBorrowerLimit();
        BigDecimal utilized =
                req.getUtilizedLimit() != null ? req.getUtilizedLimit() : BigDecimal.ZERO;
        BigDecimal available =
                req.getAvailableLimit() != null ? req.getAvailableLimit() : limit.subtract(utilized);

        Optional<SubProgramBorrower> existing =
                subProgramBorrowerRepository.findBySubProgramIdAndBorrowerId(subProgramId, borrowerId);
        if (existing.isPresent()) {
            SubProgramBorrower m = existing.get();
            if (!STATUS_PENDING.equals(m.getStatus())) {
                throw new RuntimeException(
                        "Sub-program borrower link exists with status "
                                + m.getStatus()
                                + "; LOS cannot modify non-pending links");
            }
            m.setBorrowerLimit(limit);
            m.setUtilizedLimit(utilized);
            m.setAvailableLimit(available);
            subProgramBorrowerRepository.save(m);
            return LosSubProgramBorrowerLinkResponse.builder()
                    .plpSubProgramBorrowerId(m.getId())
                    .status(m.getStatus())
                    .created(false)
                    .updated(true)
                    .build();
        }

        SubProgramBorrower membership =
                SubProgramBorrower.builder()
                        .subProgramId(subProgramId)
                        .borrowerId(borrowerId)
                        .borrowerLimit(limit)
                        .utilizedLimit(utilized)
                        .availableLimit(available)
                        .status(STATUS_PENDING)
                        .build();
        SubProgramBorrower saved = subProgramBorrowerRepository.save(membership);
        log.info(
                "LOS sub-program borrower pending link created: subProgram={} borrower={}",
                subProgramId,
                borrowerId);
        return LosSubProgramBorrowerLinkResponse.builder()
                .plpSubProgramBorrowerId(saved.getId())
                .status(saved.getStatus())
                .created(true)
                .updated(false)
                .build();
    }

    private static String normalize(String raw) {
        return raw == null ? "" : raw.trim();
    }
}
