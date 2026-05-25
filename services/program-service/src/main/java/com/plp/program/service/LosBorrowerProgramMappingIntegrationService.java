package com.plp.program.service;

import com.plp.program.integration.los.LosIntegrationResourceTypes;
import com.plp.program.model.dto.integration.LosBorrowerProgramMappingUpsertRequest;
import com.plp.program.model.dto.integration.LosBorrowerProgramMappingUpsertResponse;
import com.plp.program.model.entity.Borrower;
import com.plp.program.model.entity.BorrowerProgramMapping;
import com.plp.program.model.entity.SubProgram;
import com.plp.program.model.enums.BorrowerProgramMappingStatus;
import com.plp.program.repository.BorrowerProgramMappingRepository;
import com.plp.program.repository.BorrowerRepository;
import com.plp.program.repository.SubProgramRepository;
import com.plp.program.service.audit.LosSyncAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LosBorrowerProgramMappingIntegrationService {

    private final BorrowerProgramMappingRepository borrowerProgramMappingRepository;
    private final BorrowerRepository borrowerRepository;
    private final SubProgramRepository subProgramRepository;
    private final LosSyncAuditService losSyncAuditService;

    @Transactional
    public LosBorrowerProgramMappingUpsertResponse upsert(LosBorrowerProgramMappingUpsertRequest req) {
        String sourceSystem = normalize(req.getSourceSystem());
        String losApplicationId = normalize(req.getLosApplicationId());
        String extKey = "losApplication:" + losApplicationId;
        try {
            LosBorrowerProgramMappingUpsertResponse response = upsertInternal(req, sourceSystem, losApplicationId);
            losSyncAuditService.recordSuccess(
                    LosIntegrationResourceTypes.BORROWER_PROGRAM_MAPPING,
                    response.getPlpBorrowerProgramMappingId(),
                    sourceSystem,
                    extKey,
                    req,
                    response);
            return response;
        } catch (RuntimeException e) {
            losSyncAuditService.recordFailure(
                    LosIntegrationResourceTypes.BORROWER_PROGRAM_MAPPING,
                    null,
                    sourceSystem,
                    extKey,
                    req,
                    e.getMessage());
            throw e;
        }
    }

    private LosBorrowerProgramMappingUpsertResponse upsertInternal(
            LosBorrowerProgramMappingUpsertRequest req, String sourceSystem, String losApplicationId) {

        validateValidity(req.getValidFrom(), req.getValidTo());

        Optional<BorrowerProgramMapping> existing =
                borrowerProgramMappingRepository.findBySourceSystemAndLosApplicationId(sourceSystem, losApplicationId);
        if (existing.isPresent()) {
            BorrowerProgramMapping m = existing.get();
            assertConsistent(m, req);
            return LosBorrowerProgramMappingUpsertResponse.builder()
                    .plpBorrowerProgramMappingId(m.getId())
                    .mappingStatus(m.getStatus())
                    .created(false)
                    .updated(false)
                    .build();
        }

        Borrower borrower =
                borrowerRepository
                        .findById(req.getPlpBorrowerId())
                        .orElseThrow(() -> new RuntimeException("Borrower not found"));
        SubProgram subProgram =
                subProgramRepository
                        .findById(req.getPlpSubProgramId())
                        .orElseThrow(() -> new RuntimeException("Sub-program not found"));

        validateGraph(borrower, subProgram, req);

        Optional<BorrowerProgramMapping> raced =
                borrowerProgramMappingRepository.findBySourceSystemAndLosApplicationId(sourceSystem, losApplicationId);
        if (raced.isPresent()) {
            BorrowerProgramMapping m = raced.get();
            assertConsistent(m, req);
            return LosBorrowerProgramMappingUpsertResponse.builder()
                    .plpBorrowerProgramMappingId(m.getId())
                    .mappingStatus(m.getStatus())
                    .created(false)
                    .updated(false)
                    .build();
        }

        BorrowerProgramMapping mapping =
                BorrowerProgramMapping.builder()
                        .sourceSystem(sourceSystem)
                        .losApplicationId(losApplicationId)
                        .losBorrowerId(normalize(req.getLosBorrowerId()))
                        .borrowerId(borrower.getId())
                        .programId(req.getPlpProgramId())
                        .subProgramId(subProgram.getId())
                        .approvedLimit(req.getApprovedLimit())
                        .validFrom(req.getValidFrom())
                        .validTo(req.getValidTo())
                        .status(BorrowerProgramMappingStatus.PENDING_APPROVAL)
                        .build();

        BorrowerProgramMapping saved = borrowerProgramMappingRepository.save(mapping);
        log.info(
                "LOS borrower-program mapping created: sourceSystem={} losApplicationId={} mappingId={}",
                sourceSystem,
                losApplicationId,
                saved.getId());

        return LosBorrowerProgramMappingUpsertResponse.builder()
                .plpBorrowerProgramMappingId(saved.getId())
                .mappingStatus(saved.getStatus())
                .created(true)
                .updated(false)
                .build();
    }

    private static void validateGraph(
            Borrower borrower, SubProgram subProgram, LosBorrowerProgramMappingUpsertRequest req) {
        if (!borrower.getProgramId().equals(req.getPlpProgramId())) {
            throw new RuntimeException("Borrower programId does not match plpProgramId");
        }
        if (!subProgram.getProgramId().equals(req.getPlpProgramId())) {
            throw new RuntimeException("Sub-program does not belong to plpProgramId");
        }
        if (!borrower.getAnchorId().equals(subProgram.getAnchorId())) {
            throw new RuntimeException("Borrower anchor does not match sub-program anchor");
        }
    }

    private static void assertConsistent(BorrowerProgramMapping existing, LosBorrowerProgramMappingUpsertRequest req) {
        if (!existing.getBorrowerId().equals(req.getPlpBorrowerId())) {
            throw new RuntimeException("Existing mapping borrower mismatch for LOS application id");
        }
        if (!existing.getProgramId().equals(req.getPlpProgramId())) {
            throw new RuntimeException("Existing mapping program mismatch for LOS application id");
        }
        if (!existing.getSubProgramId().equals(req.getPlpSubProgramId())) {
            throw new RuntimeException("Existing mapping sub-program mismatch for LOS application id");
        }
        if (!existing.getLosBorrowerId().equals(normalize(req.getLosBorrowerId()))) {
            throw new RuntimeException("Existing mapping losBorrowerId mismatch");
        }
        if (existing.getApprovedLimit().compareTo(req.getApprovedLimit()) != 0
                || !existing.getValidFrom().equals(req.getValidFrom())
                || !existing.getValidTo().equals(req.getValidTo())) {
            throw new RuntimeException(
                    "Existing borrower-program mapping differs; idempotent retry requires identical limits and validity");
        }
    }

    private static void validateValidity(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new RuntimeException("validFrom must be on or before validTo");
        }
    }

    private static String normalize(String raw) {
        return raw == null ? "" : raw.trim();
    }
}
