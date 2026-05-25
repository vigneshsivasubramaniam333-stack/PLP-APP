package com.plp.program.service;

import com.plp.program.integration.los.LosIntegrationResourceTypes;
import com.plp.program.model.dto.integration.LosProgramUpsertRequest;
import com.plp.program.model.dto.integration.LosProgramUpsertResponse;
import com.plp.program.model.entity.Program;
import com.plp.program.repository.AnchorRepository;
import com.plp.program.repository.ProgramRepository;
import com.plp.program.service.audit.LosSyncAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LosProgramIntegrationService {

    private final ProgramRepository programRepository;
    private final ProgramService programService;
    private final AnchorRepository anchorRepository;
    private final LosSyncAuditService losSyncAuditService;

    @Transactional
    public LosProgramUpsertResponse upsert(LosProgramUpsertRequest req) {
        String sourceSystem = normalize(req.getSourceSystem());
        String extKey = externalKey(req);
        try {
            LosProgramUpsertResponse response = upsertInternal(req, sourceSystem);
            losSyncAuditService.recordSuccess(
                    LosIntegrationResourceTypes.PROGRAM,
                    response.getPlpProgramId(),
                    sourceSystem,
                    extKey,
                    req,
                    response);
            return response;
        } catch (RuntimeException e) {
            losSyncAuditService.recordFailure(
                    LosIntegrationResourceTypes.PROGRAM,
                    null,
                    sourceSystem,
                    extKey,
                    req,
                    e.getMessage());
            throw e;
        }
    }

    private LosProgramUpsertResponse upsertInternal(LosProgramUpsertRequest req, String sourceSystem) {
        String losPid = normalizeNullable(req.getLosProgramId());
        String code = normalize(req.getProgramCode());

        if (losPid != null && !losPid.isBlank()) {
            Optional<Program> byLos = programRepository.findBySourceSystemAndLosProgramId(sourceSystem, losPid);
            if (byLos.isPresent()) {
                Program program = byLos.get();
                if (!program.getProgramCode().equalsIgnoreCase(code)) {
                    throw new RuntimeException(
                            "LOS program identity already linked; programCode cannot change (existing="
                                    + program.getProgramCode()
                                    + ", requested="
                                    + code
                                    + ")");
                }
                assertProgramConsistent(program, req);
                applyProgramUpdates(program, req);
                programRepository.save(program);
                return LosProgramUpsertResponse.builder()
                        .plpProgramId(program.getId())
                        .programCode(program.getProgramCode())
                        .created(false)
                        .updated(true)
                        .build();
            }
        }

        Optional<Program> byCode = programRepository.findByProgramCode(code);
        if (byCode.isPresent()) {
            Program program = byCode.get();
            ensureProgramLosLinkCompatible(program, sourceSystem, losPid);
            if (losPid != null && !losPid.isBlank()) {
                program.setSourceSystem(sourceSystem);
                program.setLosProgramId(losPid);
            }
            assertProgramConsistent(program, req);
            applyProgramUpdates(program, req);
            programRepository.save(program);
            return LosProgramUpsertResponse.builder()
                    .plpProgramId(program.getId())
                    .programCode(program.getProgramCode())
                    .created(false)
                    .updated(true)
                    .build();
        }

        if (req.getAnchorId() != null) {
            anchorRepository.findById(req.getAnchorId()).orElseThrow(() -> new RuntimeException("Anchor not found"));
        }

        Program created =
                Program.builder()
                        .programCode(code)
                        .programName(req.getProgramName().trim())
                        .productType(req.getProductType())
                        .lenderId(req.getLenderId())
                        .programLimit(req.getProgramLimit())
                        .anchorLimit(req.getAnchorLimit() != null ? req.getAnchorLimit() : BigDecimal.ZERO)
                        .maxBorrowerLimit(req.getMaxBorrowerLimit())
                        .anchorId(req.getAnchorId())
                        .sourceSystem(losPid != null && !losPid.isBlank() ? sourceSystem : null)
                        .losProgramId(losPid != null && !losPid.isBlank() ? losPid : null)
                        .defaultInterestRate(req.getDefaultInterestRate())
                        .maxTenureDays(req.getMaxTenureDays())
                        .validFrom(req.getValidFrom())
                        .validTo(req.getValidTo())
                        .lmsEntryIn(normalizeLmsEntry(req.getLmsEntryIn()))
                        .encoreProductCode(trimOrNull(req.getEncoreProductCode()))
                        .build();
        Program saved = programService.createProgram(created);
        log.info("LOS program created via integration: {} ({})", saved.getProgramCode(), saved.getId());
        return LosProgramUpsertResponse.builder()
                .plpProgramId(saved.getId())
                .programCode(saved.getProgramCode())
                .created(true)
                .updated(false)
                .build();
    }

    private static void ensureProgramLosLinkCompatible(Program program, String sourceSystem, String losPid) {
        if (losPid == null || losPid.isBlank()) {
            return;
        }
        String existingLos = program.getLosProgramId();
        String existingSrc = program.getSourceSystem();
        boolean full =
                existingLos != null
                        && !existingLos.isBlank()
                        && existingSrc != null
                        && !existingSrc.isBlank();
        if (!full) {
            return;
        }
        if (existingSrc.equals(sourceSystem) && existingLos.equals(losPid)) {
            return;
        }
        throw new RuntimeException(
                "Program code "
                        + program.getProgramCode()
                        + " is already linked to a different LOS program identity");
    }

    private static void assertProgramConsistent(Program existing, LosProgramUpsertRequest dto) {
        if (!existing.getLenderId().equals(dto.getLenderId())) {
            throw new RuntimeException(
                    "Existing program has a different lenderId for code " + dto.getProgramCode());
        }
        if (existing.getProductType() != dto.getProductType()) {
            throw new RuntimeException(
                    "Existing program has a different productType for code " + dto.getProgramCode());
        }
        UUID dtoAnchor = dto.getAnchorId();
        UUID existingAnchor = existing.getAnchorId();
        if (!Objects.equals(dtoAnchor, existingAnchor)) {
            throw new RuntimeException(
                    "Existing program anchorId does not match payload for code " + dto.getProgramCode());
        }
    }

    private static void applyProgramUpdates(Program program, LosProgramUpsertRequest dto) {
        program.setProgramName(dto.getProgramName().trim());
        program.setProgramLimit(dto.getProgramLimit());
        program.setMaxBorrowerLimit(dto.getMaxBorrowerLimit());
        if (dto.getAnchorLimit() != null) {
            program.setAnchorLimit(dto.getAnchorLimit());
        }
        if (dto.getDefaultInterestRate() != null) {
            program.setDefaultInterestRate(dto.getDefaultInterestRate());
        }
        if (dto.getMaxTenureDays() != null) {
            program.setMaxTenureDays(dto.getMaxTenureDays());
        }
        if (dto.getValidFrom() != null) {
            program.setValidFrom(dto.getValidFrom());
        }
        if (dto.getValidTo() != null) {
            program.setValidTo(dto.getValidTo());
        }
        if (dto.getLmsEntryIn() != null && !dto.getLmsEntryIn().isBlank()) {
            program.setLmsEntryIn(normalizeLmsEntry(dto.getLmsEntryIn()));
        }
        if (dto.getEncoreProductCode() != null) {
            program.setEncoreProductCode(trimOrNull(dto.getEncoreProductCode()));
        }
    }

    private static String normalizeLmsEntry(String raw) {
        if (raw == null || raw.isBlank()) {
            return "NO";
        }
        String v = raw.trim().toUpperCase();
        if ("YES".equals(v) || "NO".equals(v)) {
            return v;
        }
        throw new RuntimeException("lmsEntryIn must be YES or NO");
    }

    private static String trimOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim();
    }

    private static String externalKey(LosProgramUpsertRequest req) {
        String los = normalizeNullable(req.getLosProgramId());
        if (los != null && !los.isBlank()) {
            return "losProgram:" + los;
        }
        return "programCode:" + normalize(req.getProgramCode());
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
