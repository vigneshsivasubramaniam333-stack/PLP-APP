package com.plp.program.service;

import com.plp.program.integration.los.LosIntegrationResourceTypes;
import com.plp.program.model.dto.integration.LosSubProgramUpsertRequest;
import com.plp.program.model.dto.integration.LosSubProgramUpsertResponse;
import com.plp.program.model.entity.Program;
import com.plp.program.model.entity.SubProgram;
import com.plp.program.repository.AnchorRepository;
import com.plp.program.repository.ProgramRepository;
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
public class LosSubProgramIntegrationService {

    private final SubProgramRepository subProgramRepository;
    private final ProgramRepository programRepository;
    private final SubProgramService subProgramService;
    private final AnchorRepository anchorRepository;
    private final LosSyncAuditService losSyncAuditService;

    @Transactional
    public LosSubProgramUpsertResponse upsert(LosSubProgramUpsertRequest req) {
        String sourceSystem = normalize(req.getSourceSystem());
        String extKey = externalKey(req);
        try {
            LosSubProgramUpsertResponse response = upsertInternal(req, sourceSystem);
            losSyncAuditService.recordSuccess(
                    LosIntegrationResourceTypes.SUB_PROGRAM,
                    response.getPlpSubProgramId(),
                    sourceSystem,
                    extKey,
                    req,
                    response);
            return response;
        } catch (RuntimeException e) {
            losSyncAuditService.recordFailure(
                    LosIntegrationResourceTypes.SUB_PROGRAM,
                    null,
                    sourceSystem,
                    extKey,
                    req,
                    e.getMessage());
            throw e;
        }
    }

    private LosSubProgramUpsertResponse upsertInternal(LosSubProgramUpsertRequest req, String sourceSystem) {
        String losSid = normalizeNullable(req.getLosSubProgramId());
        String code = normalize(req.getSubProgramCode());

        Program program = resolveProgram(req);
        if (!program.getLenderId().equals(req.getLenderId())) {
            throw new RuntimeException("lenderId does not match parent program lender");
        }

        anchorRepository.findById(req.getAnchorId()).orElseThrow(() -> new RuntimeException("Anchor not found"));

        if (losSid != null && !losSid.isBlank()) {
            Optional<SubProgram> byLos =
                    subProgramRepository.findBySourceSystemAndLosSubProgramId(sourceSystem, losSid);
            if (byLos.isPresent()) {
                SubProgram sp = byLos.get();
                if (!sp.getCode().equalsIgnoreCase(code)) {
                    throw new RuntimeException(
                            "LOS sub-program identity already linked; subProgramCode cannot change (existing="
                                    + sp.getCode()
                                    + ", requested="
                                    + code
                                    + ")");
                }
                assertParentAndAnchor(program, sp, req);
                applySubProgramUpdates(sp, program, req);
                subProgramRepository.save(sp);
                return LosSubProgramUpsertResponse.builder()
                        .plpSubProgramId(sp.getId())
                        .subProgramCode(sp.getCode())
                        .created(false)
                        .updated(true)
                        .build();
            }
        }

        Optional<SubProgram> byCode = subProgramRepository.findByCode(code);
        if (byCode.isPresent()) {
            SubProgram sp = byCode.get();
            if (!sp.getProgramId().equals(program.getId())) {
                throw new RuntimeException("Sub-program code " + code + " belongs to a different program");
            }
            ensureSubProgramLosLinkCompatible(sp, sourceSystem, losSid);
            if (losSid != null && !losSid.isBlank()) {
                sp.setSourceSystem(sourceSystem);
                sp.setLosSubProgramId(losSid);
            }
            assertParentAndAnchor(program, sp, req);
            applySubProgramUpdates(sp, program, req);
            subProgramRepository.save(sp);
            return LosSubProgramUpsertResponse.builder()
                    .plpSubProgramId(sp.getId())
                    .subProgramCode(sp.getCode())
                    .created(false)
                    .updated(true)
                    .build();
        }

        SubProgram built =
                SubProgram.builder()
                        .programId(program.getId())
                        .anchorId(req.getAnchorId())
                        .lenderId(req.getLenderId())
                        .code(code)
                        .name(req.getName().trim())
                        .flowType(req.getFlowType())
                        .anchorRole(req.getAnchorRole())
                        .borrowerRole(req.getBorrowerRole())
                        .subProgramLimit(req.getSubProgramLimit())
                        .sourceSystem(losSid != null && !losSid.isBlank() ? sourceSystem : null)
                        .losSubProgramId(losSid != null && !losSid.isBlank() ? losSid : null)
                        .interestRate(resolveInterestRate(program, req))
                        .maxTenureDays(resolveMaxTenureDays(program, req))
                        .build();

        SubProgram saved = subProgramService.createSubProgram(built);
        log.info(
                "LOS sub-program interest rate persisted: subProgramId={} interestRate={}",
                saved.getId(),
                saved.getInterestRate());
        log.info("LOS sub-program created via integration: {} ({})", saved.getCode(), saved.getId());
        return LosSubProgramUpsertResponse.builder()
                .plpSubProgramId(saved.getId())
                .subProgramCode(saved.getCode())
                .created(true)
                .updated(false)
                .build();
    }

    private Program resolveProgram(LosSubProgramUpsertRequest req) {
        if (req.getPlpProgramId() != null) {
            return programRepository
                    .findById(req.getPlpProgramId())
                    .orElseThrow(() -> new RuntimeException("Program not found: " + req.getPlpProgramId()));
        }
        String pc = normalize(req.getProgramCode());
        return programRepository
                .findByProgramCode(pc)
                .orElseThrow(() -> new RuntimeException("Program not found: " + pc));
    }

    private static void assertParentAndAnchor(Program program, SubProgram sp, LosSubProgramUpsertRequest req) {
        if (!sp.getProgramId().equals(program.getId())) {
            throw new RuntimeException("Sub-program does not belong to resolved program");
        }
        if (!sp.getAnchorId().equals(req.getAnchorId())) {
            throw new RuntimeException("Existing sub-program anchorId does not match payload for code " + req.getSubProgramCode());
        }
        UUID spLender = sp.getLenderId();
        if (spLender != null && !spLender.equals(req.getLenderId())) {
            throw new RuntimeException("Existing sub-program lenderId does not match payload");
        }
    }

    private static void ensureSubProgramLosLinkCompatible(SubProgram sp, String sourceSystem, String losSid) {
        if (losSid == null || losSid.isBlank()) {
            return;
        }
        String existingLos = sp.getLosSubProgramId();
        String existingSrc = sp.getSourceSystem();
        boolean full =
                existingLos != null
                        && !existingLos.isBlank()
                        && existingSrc != null
                        && !existingSrc.isBlank();
        if (!full) {
            return;
        }
        if (existingSrc.equals(sourceSystem) && existingLos.equals(losSid)) {
            return;
        }
        throw new RuntimeException(
                "Sub-program code "
                        + sp.getCode()
                        + " is already linked to a different LOS sub-program identity");
    }

    private static void applySubProgramUpdates(SubProgram sp, Program program, LosSubProgramUpsertRequest req) {
        sp.setName(req.getName().trim());
        sp.setFlowType(req.getFlowType());
        sp.setAnchorRole(req.getAnchorRole());
        sp.setBorrowerRole(req.getBorrowerRole());
        sp.setSubProgramLimit(req.getSubProgramLimit());
        sp.setLenderId(req.getLenderId());
        sp.setInterestRate(resolveInterestRate(program, req));
        if (req.getMaxTenureDays() != null) {
            sp.setMaxTenureDays(req.getMaxTenureDays());
        } else if (sp.getMaxTenureDays() == null && program.getMaxTenureDays() != null) {
            sp.setMaxTenureDays(program.getMaxTenureDays());
        }
    }

    private static BigDecimal resolveInterestRate(Program program, LosSubProgramUpsertRequest req) {
        if (req.getInterestRate() != null) {
            return req.getInterestRate();
        }
        return program.getDefaultInterestRate();
    }

    private static Integer resolveMaxTenureDays(Program program, LosSubProgramUpsertRequest req) {
        if (req.getMaxTenureDays() != null) {
            return req.getMaxTenureDays();
        }
        return program.getMaxTenureDays();
    }

    private static String externalKey(LosSubProgramUpsertRequest req) {
        String los = normalizeNullable(req.getLosSubProgramId());
        if (los != null && !los.isBlank()) {
            return "losSubProgram:" + los;
        }
        return "subProgramCode:" + normalize(req.getSubProgramCode());
    }

    private static String normalize(String raw) {
        return raw == null ? "" : raw.trim();
    }

    private static String normalizeNullable(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim();
    }
}
