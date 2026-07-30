package com.plp.program.service;

import com.plp.program.integration.los.LosIntegrationResourceTypes;
import com.plp.program.model.dto.integration.LosAnchorSyncRequest;
import com.plp.program.model.dto.integration.LosAnchorSyncRequest.LosAnchorPayload;
import com.plp.program.model.dto.integration.LosAnchorSyncResponse;
import com.plp.program.model.entity.Anchor;
import com.plp.program.model.enums.AnchorOnboardingStatus;
import com.plp.program.model.enums.AnchorStatus;
import com.plp.program.integration.iam.IamUserProvisioner;
import com.plp.program.repository.AnchorRepository;
import com.plp.program.service.audit.LosSyncAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LosAnchorIntegrationService {

    private static final String DEFAULT_ENTITY_TYPE = "CORPORATE";

    private final AnchorRepository anchorRepository;
    private final LosSyncAuditService losSyncAuditService;
    private final IamUserProvisioner iamUserProvisioner;

    @Transactional
    public LosAnchorSyncResponse syncAnchor(LosAnchorSyncRequest req) {
        String sourceSystem = normalize(req.getSourceSystem());
        String losAnchorId = normalize(req.getLosAnchorId());
        String extKey = "losAnchor:" + losAnchorId;
        try {
            LosAnchorSyncResponse response = syncInternal(req, sourceSystem, losAnchorId);
            losSyncAuditService.recordSuccess(
                    LosIntegrationResourceTypes.ANCHOR,
                    response.getPlpAnchorId(),
                    sourceSystem,
                    extKey,
                    req,
                    response);
            return response;
        } catch (RuntimeException e) {
            losSyncAuditService.recordFailure(
                    LosIntegrationResourceTypes.ANCHOR,
                    null,
                    sourceSystem,
                    extKey,
                    req,
                    e.getMessage());
            throw e;
        }
    }

    private LosAnchorSyncResponse syncInternal(LosAnchorSyncRequest req, String sourceSystem, String losAnchorId) {
        LosAnchorPayload payload = req.getAnchor();
        String code = normalize(payload.getCode());
        boolean provisionAtNotify = Boolean.TRUE.equals(req.getProvisionAtNotify());

        Optional<Anchor> byLos = anchorRepository.findBySourceSystemAndLosAnchorId(sourceSystem, losAnchorId);
        if (byLos.isPresent()) {
            Anchor anchor = byLos.get();
            assertCodeMatchesIfRequested(anchor, code);
            applyLosUpdates(anchor, payload, req);
            anchorRepository.save(anchor);
            IamUserProvisioner.ProvisionResult provision = provisionAnchorPortalUser(anchor, provisionAtNotify);
            return toResponse(anchor, false, true, provision);
        }

        Optional<Anchor> byCode = anchorRepository.findByAnchorCode(code);
        if (byCode.isPresent()) {
            Anchor anchor = byCode.get();
            ensureLosLinkCompatible(anchor, sourceSystem, losAnchorId);
            anchor.setSourceSystem(sourceSystem);
            anchor.setLosAnchorId(losAnchorId);
            applyLosUpdates(anchor, payload, req);
            anchorRepository.save(anchor);
            IamUserProvisioner.ProvisionResult provision = provisionAnchorPortalUser(anchor, provisionAtNotify);
            log.info("LOS anchor linked by code: anchorId={} code={}", anchor.getId(), anchor.getAnchorCode());
            return toResponse(anchor, false, true, provision);
        }

        Anchor created =
                Anchor.builder()
                        .anchorCode(code)
                        .entityName(payload.getName().trim())
                        .entityType(DEFAULT_ENTITY_TYPE)
                        .gstin(trimOrNull(payload.getGstin()))
                        .pan(trimOrNull(payload.getPan()))
                        .contactEmail(trimOrNull(payload.getEmail()))
                        .contactPhone(trimOrNull(payload.getMobile()))
                        .address(buildAddressJson(payload.getAddress()))
                        .sourceSystem(sourceSystem)
                        .losAnchorId(losAnchorId)
                        .losApplicationId(trimOrNull(req.getLosApplicationId()))
                        .status(AnchorStatus.ACTIVE)
                        .onboardingStatus(resolveOnboarding(req.getOnboardingStatus(),
                                provisionAtNotify ? AnchorOnboardingStatus.INVITED : null))
                        .build();

        Anchor saved = anchorRepository.save(created);
        log.info("LOS anchor created: anchorId={} code={}", saved.getId(), saved.getAnchorCode());
        IamUserProvisioner.ProvisionResult provision = provisionAnchorPortalUser(saved, true);
        return toResponse(saved, true, false, provision);
    }

    private static LosAnchorSyncResponse toResponse(
            Anchor anchor, boolean created, Boolean updated, IamUserProvisioner.ProvisionResult provision) {
        LosAnchorSyncResponse.LosAnchorSyncResponseBuilder b = LosAnchorSyncResponse.builder()
                .plpAnchorId(anchor.getId())
                .anchorCode(anchor.getAnchorCode())
                .created(created)
                .updated(updated)
                .onboardingStatus(anchor.getOnboardingStatus() != null
                        ? anchor.getOnboardingStatus().name()
                        : null);
        if (provision != null) {
            b.userId(provision.getUserId())
                    .temporaryPassword(provision.getTemporaryPassword())
                    .passwordResetRequired(provision.getPasswordResetRequired());
        }
        return b.build();
    }

    private static void assertCodeMatchesIfRequested(Anchor anchor, String requestedCode) {
        if (!anchor.getAnchorCode().equalsIgnoreCase(requestedCode)) {
            throw new RuntimeException(
                    "Anchor already linked for this LOS identity; anchorCode cannot be changed (existing="
                            + anchor.getAnchorCode()
                            + ", requested="
                            + requestedCode
                            + ")");
        }
    }

    private static void ensureLosLinkCompatible(Anchor anchor, String sourceSystem, String losAnchorId) {
        String existingLos = anchor.getLosAnchorId();
        String existingSrc = anchor.getSourceSystem();
        boolean hasLos = existingLos != null && !existingLos.isBlank();
        boolean hasSrc = existingSrc != null && !existingSrc.isBlank();
        boolean fullyLinked = hasLos && hasSrc;
        if (!fullyLinked) {
            return;
        }
        if (existingSrc.equals(sourceSystem) && existingLos.equals(losAnchorId)) {
            return;
        }
        throw new RuntimeException(
                "Anchor code "
                        + anchor.getAnchorCode()
                        + " is already linked to a different LOS identity");
    }

    private static void applyLosUpdates(Anchor anchor, LosAnchorPayload payload, LosAnchorSyncRequest req) {
        anchor.setEntityName(payload.getName().trim());
        if (payload.getPan() != null && !payload.getPan().isBlank()) {
            anchor.setPan(payload.getPan().trim());
        }
        if (payload.getGstin() != null && !payload.getGstin().isBlank()) {
            anchor.setGstin(payload.getGstin().trim());
        }
        if (payload.getEmail() != null && !payload.getEmail().isBlank()) {
            anchor.setContactEmail(payload.getEmail().trim());
        }
        if (payload.getMobile() != null && !payload.getMobile().isBlank()) {
            anchor.setContactPhone(payload.getMobile().trim());
        }
        if (payload.getAddress() != null && !payload.getAddress().isBlank()) {
            anchor.setAddress(buildAddressJson(payload.getAddress()));
        }
        AnchorOnboardingStatus onboarding = resolveOnboarding(req.getOnboardingStatus(), null);
        if (onboarding != null) {
            anchor.setOnboardingStatus(onboarding);
        } else if (Boolean.TRUE.equals(req.getProvisionAtNotify()) && anchor.getOnboardingStatus() == null) {
            anchor.setOnboardingStatus(AnchorOnboardingStatus.INVITED);
        }
        if (req.getLosApplicationId() != null && !req.getLosApplicationId().isBlank()) {
            anchor.setLosApplicationId(req.getLosApplicationId().trim());
        }
    }

    private static AnchorOnboardingStatus resolveOnboarding(String raw, AnchorOnboardingStatus fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return AnchorOnboardingStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static Map<String, Object> buildAddressJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return Map.of("text", raw.trim());
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim();
    }

    private static String trimOrNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }

    private IamUserProvisioner.ProvisionResult provisionAnchorPortalUser(Anchor anchor, boolean force) {
        if (!force && (anchor.getContactEmail() == null || anchor.getContactEmail().isBlank())) {
            return IamUserProvisioner.ProvisionResult.empty();
        }
        return iamUserProvisioner.provisionAnchorAdmin(
                anchor.getId(),
                anchor.getContactEmail(),
                anchor.getEntityName(),
                anchor.getContactPhone());
    }
}
