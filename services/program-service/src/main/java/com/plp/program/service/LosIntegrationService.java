package com.plp.program.service;

import com.plp.program.model.dto.integration.LosBorrowerProgramMappingUpsertRequest;
import com.plp.program.model.dto.integration.LosBorrowerProgramMappingUpsertResponse;
import com.plp.program.model.dto.integration.LosBorrowerUpsertRequest;
import com.plp.program.model.dto.integration.LosBorrowerUpsertRequest.LosBorrowerPayload;
import com.plp.program.model.dto.integration.LosBorrowerUpsertResponse;
import com.plp.program.model.dto.integration.LosProgramBorrowerLinkRequest;
import com.plp.program.model.dto.integration.LosProgramBorrowerLinkRequest.LosBorrowerDetailsDto;
import com.plp.program.model.dto.integration.LosProgramBorrowerLinkRequest.LosProgramDetailsDto;
import com.plp.program.model.dto.integration.LosProgramBorrowerLinkRequest.LosSubProgramDetailsDto;
import com.plp.program.model.dto.integration.LosProgramBorrowerLinkResponse;
import com.plp.program.model.dto.integration.LosProgramUpsertRequest;
import com.plp.program.model.dto.integration.LosProgramUpsertResponse;
import com.plp.program.model.dto.integration.LosSubProgramBorrowerLinkRequest;
import com.plp.program.model.dto.integration.LosSubProgramUpsertRequest;
import com.plp.program.model.dto.integration.LosSubProgramUpsertResponse;
import com.plp.program.model.entity.BorrowerProgramMapping;
import com.plp.program.repository.BorrowerProgramMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates the legacy aggregate LOS endpoint by delegating to granular integration services.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LosIntegrationService {

    private final BorrowerProgramMappingRepository borrowerProgramMappingRepository;
    private final LosProgramIntegrationService losProgramIntegrationService;
    private final LosSubProgramIntegrationService losSubProgramIntegrationService;
    private final LosBorrowerIntegrationService losBorrowerIntegrationService;
    private final LosSubProgramBorrowerLinkIntegrationService losSubProgramBorrowerLinkIntegrationService;
    private final LosBorrowerProgramMappingIntegrationService losBorrowerProgramMappingIntegrationService;

    @Transactional
    public LosProgramBorrowerLinkResponse linkProgramBorrower(LosProgramBorrowerLinkRequest req) {
        String sourceSystem = normalizeKey(req.getSourceSystem());
        String losApplicationId = normalizeKey(req.getLosApplicationId());
        String losBorrowerId = normalizeKey(req.getLosBorrowerId());

        validateValidity(req.getValidFrom(), req.getValidTo());

        Optional<BorrowerProgramMapping> existingMapping =
                borrowerProgramMappingRepository.findBySourceSystemAndLosApplicationId(sourceSystem, losApplicationId);
        if (existingMapping.isPresent()) {
            return toResponse(existingMapping.get(), false, Boolean.FALSE);
        }

        LosProgramUpsertRequest programReq = toProgramUpsertRequest(sourceSystem, req.getProgram());
        LosProgramUpsertResponse programResp = losProgramIntegrationService.upsert(programReq);

        LosSubProgramUpsertRequest subReq =
                toSubProgramUpsertRequest(
                        sourceSystem,
                        programResp.getPlpProgramId(),
                        req.getProgram().getLenderId(),
                        req.getSubProgram());
        LosSubProgramUpsertResponse subResp = losSubProgramIntegrationService.upsert(subReq);

        LosBorrowerUpsertRequest borrowerReq =
                toBorrowerUpsertRequest(
                        sourceSystem,
                        losBorrowerId,
                        req.getBorrower(),
                        programResp.getPlpProgramId(),
                        req.getSubProgram().getAnchorId());
        LosBorrowerUpsertResponse borrowerResp = losBorrowerIntegrationService.upsert(borrowerReq);

        LosSubProgramBorrowerLinkRequest linkReq =
                toSubProgramBorrowerLinkRequest(
                        sourceSystem,
                        subResp.getPlpSubProgramId(),
                        borrowerResp.getPlpBorrowerId(),
                        req.getApprovedLimit());
        losSubProgramBorrowerLinkIntegrationService.link(linkReq);

        Optional<BorrowerProgramMapping> raced =
                borrowerProgramMappingRepository.findBySourceSystemAndLosApplicationId(sourceSystem, losApplicationId);
        if (raced.isPresent()) {
            return toResponse(raced.get(), false, Boolean.FALSE);
        }

        LosBorrowerProgramMappingUpsertRequest mappingReq =
                toMappingUpsertRequest(
                        sourceSystem,
                        losApplicationId,
                        losBorrowerId,
                        borrowerResp.getPlpBorrowerId(),
                        programResp.getPlpProgramId(),
                        subResp.getPlpSubProgramId(),
                        req.getApprovedLimit(),
                        req.getValidFrom(),
                        req.getValidTo());
        LosBorrowerProgramMappingUpsertResponse mappingResp =
                losBorrowerProgramMappingIntegrationService.upsert(mappingReq);

        BorrowerProgramMapping mapping =
                borrowerProgramMappingRepository
                        .findById(mappingResp.getPlpBorrowerProgramMappingId())
                        .orElseThrow();

        log.info(
                "LOS program-borrower link completed (aggregate): sourceSystem={} losApplicationId={} mappingId={}",
                sourceSystem,
                losApplicationId,
                mapping.getId());

        return toResponse(mapping, mappingResp.isCreated(), mappingResp.getUpdated());
    }

    private static LosProgramUpsertRequest toProgramUpsertRequest(String sourceSystem, LosProgramDetailsDto dto) {
        LosProgramUpsertRequest p = new LosProgramUpsertRequest();
        p.setSourceSystem(sourceSystem);
        p.setLosProgramId(null);
        p.setProgramCode(dto.getProgramCode().trim());
        p.setProgramName(dto.getProgramName());
        p.setProductType(dto.getProductType());
        p.setLenderId(dto.getLenderId());
        p.setProgramLimit(dto.getProgramLimit());
        p.setMaxBorrowerLimit(dto.getMaxBorrowerLimit());
        p.setAnchorId(dto.getAnchorId());
        p.setAnchorLimit(dto.getAnchorLimit());
        return p;
    }

    private static LosSubProgramUpsertRequest toSubProgramUpsertRequest(
            String sourceSystem, UUID plpProgramId, UUID programLenderId, LosSubProgramDetailsDto dto) {
        LosSubProgramUpsertRequest s = new LosSubProgramUpsertRequest();
        s.setSourceSystem(sourceSystem);
        s.setLosSubProgramId(null);
        s.setPlpProgramId(plpProgramId);
        s.setProgramCode(null);
        s.setAnchorId(dto.getAnchorId());
        s.setLenderId(programLenderId);
        s.setSubProgramCode(dto.getCode().trim());
        s.setName(dto.getName());
        s.setFlowType(dto.getFlowType());
        s.setAnchorRole(dto.getAnchorRole());
        s.setBorrowerRole(dto.getBorrowerRole());
        s.setSubProgramLimit(dto.getSubProgramLimit());
        return s;
    }

    private static LosBorrowerUpsertRequest toBorrowerUpsertRequest(
            String sourceSystem,
            String losBorrowerId,
            LosBorrowerDetailsDto dto,
            UUID plpProgramId,
            UUID plpAnchorId) {
        LosBorrowerUpsertRequest b = new LosBorrowerUpsertRequest();
        b.setSourceSystem(sourceSystem);
        b.setLosBorrowerId(losBorrowerId);
        b.setPlpProgramId(plpProgramId);
        b.setPlpAnchorId(plpAnchorId);
        LosBorrowerPayload payload = new LosBorrowerPayload();
        payload.setName(dto.getName());
        payload.setEmail(dto.getEmail());
        payload.setPhone(dto.getPhone());
        payload.setPan(dto.getPan());
        b.setBorrower(payload);
        return b;
    }

    private static LosSubProgramBorrowerLinkRequest toSubProgramBorrowerLinkRequest(
            String sourceSystem, UUID subProgramId, UUID borrowerId, BigDecimal approvedLimit) {
        LosSubProgramBorrowerLinkRequest r = new LosSubProgramBorrowerLinkRequest();
        r.setSourceSystem(sourceSystem);
        r.setSubProgramId(subProgramId);
        r.setBorrowerId(borrowerId);
        r.setBorrowerLimit(approvedLimit);
        r.setUtilizedLimit(BigDecimal.ZERO);
        r.setAvailableLimit(approvedLimit);
        return r;
    }

    private static LosBorrowerProgramMappingUpsertRequest toMappingUpsertRequest(
            String sourceSystem,
            String losApplicationId,
            String losBorrowerId,
            UUID plpBorrowerId,
            UUID plpProgramId,
            UUID plpSubProgramId,
            BigDecimal approvedLimit,
            LocalDate validFrom,
            LocalDate validTo) {
        LosBorrowerProgramMappingUpsertRequest m = new LosBorrowerProgramMappingUpsertRequest();
        m.setSourceSystem(sourceSystem);
        m.setLosApplicationId(losApplicationId);
        m.setLosBorrowerId(losBorrowerId);
        m.setPlpBorrowerId(plpBorrowerId);
        m.setPlpProgramId(plpProgramId);
        m.setPlpSubProgramId(plpSubProgramId);
        m.setApprovedLimit(approvedLimit);
        m.setValidFrom(validFrom);
        m.setValidTo(validTo);
        return m;
    }

    private static void validateValidity(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new RuntimeException("validFrom must be on or before validTo");
        }
    }

    private static LosProgramBorrowerLinkResponse toResponse(
            BorrowerProgramMapping mapping, boolean created, Boolean updated) {
        return LosProgramBorrowerLinkResponse.builder()
                .plpBorrowerId(mapping.getBorrowerId())
                .plpProgramId(mapping.getProgramId())
                .plpSubProgramId(mapping.getSubProgramId())
                .plpBorrowerProgramMappingId(mapping.getId())
                .mappingStatus(mapping.getStatus())
                .created(created)
                .updated(updated)
                .build();
    }

    private static String normalizeKey(String raw) {
        return raw == null ? "" : raw.trim();
    }
}
