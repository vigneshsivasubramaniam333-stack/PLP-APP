package com.plp.program.service;

import com.plp.program.integration.los.LosAnchorApplicationClient;
import com.plp.program.integration.los.LosIntegrationException;
import com.plp.program.model.entity.Anchor;
import com.plp.program.model.enums.AnchorOnboardingStatus;
import com.plp.program.repository.AnchorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Backs the anchor onboarding portal (Phase 2): resolves the current anchor, reads/writes the
 * delegated LOS loan application via {@link LosAnchorApplicationClient}, and exposes a
 * portal-friendly summary of onboarding progress.
 *
 * <p>Anchor.onboardingStatus is normally kept in sync by LOS pushing status updates through the
 * existing {@code POST /api/v1/integrations/los/anchors} sync endpoint (see
 * {@code LosAnchorIntegrationService}) whenever the anchor notifies / submits / is sent back on
 * the LOS side. Calling {@link #submit} / {@link #resubmit} here triggers that same LOS-side flow
 * synchronously, so the local {@link Anchor#getOnboardingStatus()} reflects the new state by the
 * time this service returns.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnchorOnboardingService {

    private static final Map<AnchorOnboardingStatus, String> FRIENDLY_STAGE = Map.of(
            AnchorOnboardingStatus.INVITED, "Complete your onboarding application to get started",
            AnchorOnboardingStatus.IN_PROGRESS, "Continue your onboarding application",
            AnchorOnboardingStatus.SUBMITTED, "Your application has been submitted and is under review",
            AnchorOnboardingStatus.SENT_BACK, "Action needed: your application was sent back for corrections",
            AnchorOnboardingStatus.COMPLETED, "Onboarding complete"
    );

    private final AnchorRepository anchorRepository;
    private final LosAnchorApplicationClient losAnchorApplicationClient;

    public Map<String, Object> getSummary(UUID anchorId) {
        Anchor anchor = requireAnchor(anchorId);
        Map<String, Object> losApplication = tryLoadLosApplication(anchor);
        AnchorOnboardingStatus onboardingStatus = reconcileOnboardingStatus(anchor, losApplication);

        String losStatus = losApplication != null ? asString(losApplication.get("status")) : null;
        String applicationId = anchor.getLosApplicationId();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("onboardingStatus", onboardingStatus != null ? onboardingStatus.name() : null);
        summary.put("applicationId", applicationId);
        summary.put("applicationNumber", losApplication != null ? asString(losApplication.get("applicationNumber")) : null);
        summary.put("status", losStatus);
        summary.put("losStatus", losStatus);
        summary.put("friendlyStage", friendlyStage(onboardingStatus, losStatus));
        summary.put("canResume", canResume(onboardingStatus, losStatus));
        summary.put("forceOnboarding", forceOnboarding(onboardingStatus, losStatus));
        summary.put("menusUnlocked", menusUnlocked(onboardingStatus));
        summary.put("showMyApplication", applicationId != null && !applicationId.isBlank());
        summary.put("sendBackNotes", sendBackNotes(losApplication));
        summary.put("requiredActions", requiredActions(onboardingStatus, losStatus));
        summary.put("anchorId", anchor.getId());
        summary.put("entityName", anchor.getEntityName());
        summary.put("losIntegrationAvailable", losAnchorApplicationClient.isConfigured());
        summary.put("requestedAmount", losApplication != null ? losApplication.get("requestedAmount") : null);
        summary.put("tenureMonths", losApplication != null ? losApplication.get("tenureMonths") : null);
        summary.put("loanProduct", losApplication != null ? asString(losApplication.get("loanProduct")) : null);
        return summary;
    }

    public Map<String, Object> getApplication(UUID anchorId) {
        Anchor anchor = requireAnchor(anchorId);
        String applicationId = requireApplicationId(anchor);
        try {
            return losAnchorApplicationClient.getApplication(applicationId);
        } catch (LosIntegrationException e) {
            throw mapLosException(e);
        }
    }

    public Map<String, Object> getIntakeContext(UUID anchorId) {
        Anchor anchor = requireAnchor(anchorId);
        String applicationId = requireApplicationId(anchor);
        try {
            return losAnchorApplicationClient.getIntakeContext(applicationId);
        } catch (LosIntegrationException e) {
            throw mapLosException(e);
        }
    }

    public List<Map<String, Object>> listStates() {
        try {
            return losAnchorApplicationClient.listStates();
        } catch (LosIntegrationException e) {
            throw mapLosException(e);
        }
    }

    public List<Map<String, Object>> listCities(UUID stateId) {
        try {
            return losAnchorApplicationClient.listCities(stateId);
        } catch (LosIntegrationException e) {
            throw mapLosException(e);
        }
    }

    @Transactional
    public Map<String, Object> updateApplication(UUID anchorId, Map<String, Object> payload) {
        Anchor anchor = requireAnchor(anchorId);
        String applicationId = requireApplicationId(anchor);
        Map<String, Object> updated;
        try {
            updated = losAnchorApplicationClient.updateApplication(applicationId, payload);
        } catch (LosIntegrationException e) {
            throw mapLosException(e);
        }
        if (anchor.getOnboardingStatus() == AnchorOnboardingStatus.INVITED
                || anchor.getOnboardingStatus() == null) {
            anchor.setOnboardingStatus(AnchorOnboardingStatus.IN_PROGRESS);
            anchorRepository.save(anchor);
        }
        return updated;
    }

    public List<Map<String, Object>> listDocuments(UUID anchorId) {
        Anchor anchor = requireAnchor(anchorId);
        String applicationId = requireApplicationId(anchor);
        try {
            return losAnchorApplicationClient.listDocuments(applicationId);
        } catch (LosIntegrationException e) {
            throw mapLosException(e);
        }
    }

    public ResponseEntity<byte[]> streamDocument(UUID anchorId, UUID documentId, boolean inline) {
        Anchor anchor = requireAnchor(anchorId);
        String applicationId = requireApplicationId(anchor);
        try {
            return losAnchorApplicationClient.downloadDocument(applicationId, documentId.toString(), inline);
        } catch (LosIntegrationException e) {
            throw mapLosException(e);
        }
    }

    @Transactional
    public Map<String, Object> uploadDocument(UUID anchorId, String documentType, MultipartFile file) {
        Anchor anchor = requireAnchor(anchorId);
        String applicationId = requireApplicationId(anchor);
        if (documentType == null || documentType.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "documentType is required");
        }
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file is required");
        }
        try {
            Map<String, Object> uploaded = losAnchorApplicationClient.uploadDocument(
                    applicationId, documentType.trim(), file);
            if (anchor.getOnboardingStatus() == AnchorOnboardingStatus.INVITED
                    || anchor.getOnboardingStatus() == null) {
                anchor.setOnboardingStatus(AnchorOnboardingStatus.IN_PROGRESS);
                anchorRepository.save(anchor);
            }
            return uploaded;
        } catch (LosIntegrationException e) {
            throw mapLosException(e);
        }
    }

    @Transactional
    public Map<String, Object> submit(UUID anchorId) {
        Anchor anchor = requireAnchor(anchorId);
        String applicationId = requireApplicationId(anchor);
        try {
            losAnchorApplicationClient.submit(applicationId);
        } catch (LosIntegrationException e) {
            throw mapLosException(e);
        }
        anchor.setOnboardingStatus(AnchorOnboardingStatus.SUBMITTED);
        anchorRepository.save(anchor);
        return getSummary(anchorId);
    }

    @Transactional
    public Map<String, Object> resubmit(UUID anchorId) {
        Anchor anchor = requireAnchor(anchorId);
        String applicationId = requireApplicationId(anchor);
        try {
            losAnchorApplicationClient.resubmit(applicationId);
        } catch (LosIntegrationException e) {
            throw mapLosException(e);
        }
        // After intake send-back → ANCHOR_SUBMITTED; after doc send-back → DOC_VERIFICATION_PENDING.
        // Both are "under review" from the portal's perspective.
        anchor.setOnboardingStatus(AnchorOnboardingStatus.SUBMITTED);
        anchorRepository.save(anchor);
        return getSummary(anchorId);
    }

    private static ResponseStatusException mapLosException(LosIntegrationException e) {
        Integer status = e.getHttpStatus();
        if (status != null && status >= 400 && status < 500) {
            HttpStatus http = HttpStatus.resolve(status);
            return new ResponseStatusException(
                    http != null ? http : HttpStatus.BAD_REQUEST,
                    e.getMessage() != null ? e.getMessage() : "LOS rejected the request");
        }
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                e.getMessage() != null ? e.getMessage() : "LOS request failed");
    }

    private Anchor requireAnchor(UUID anchorId) {
        return anchorRepository.findById(anchorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Anchor not found"));
    }

    private String requireApplicationId(Anchor anchor) {
        String applicationId = anchor.getLosApplicationId();
        if (applicationId == null || applicationId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "No LOS application is linked to this anchor yet");
        }
        return applicationId;
    }

    private Map<String, Object> tryLoadLosApplication(Anchor anchor) {
        String applicationId = anchor.getLosApplicationId();
        if (applicationId == null || applicationId.isBlank() || !losAnchorApplicationClient.isConfigured()) {
            return null;
        }
        try {
            return losAnchorApplicationClient.getApplication(applicationId);
        } catch (Exception e) {
            log.warn("Could not load LOS application {} for anchor {}: {}", applicationId, anchor.getId(), e.getMessage());
            return null;
        }
    }

    /**
     * Prefer live LOS application status when available so RM send-back is reflected even if the
     * async PLP onboardingStatus push lagged. Persists when the local value needs correction.
     */
    private AnchorOnboardingStatus reconcileOnboardingStatus(Anchor anchor, Map<String, Object> losApplication) {
        AnchorOnboardingStatus local = anchor.getOnboardingStatus();
        if (losApplication == null) {
            return local;
        }
        AnchorOnboardingStatus fromLos = mapLosStatus(asString(losApplication.get("status")));
        if (fromLos == null) {
            return local;
        }
        // Don't regress IN_PROGRESS back to INVITED when LOS is still ANCHOR_CONSENT_PENDING.
        if (fromLos == AnchorOnboardingStatus.INVITED
                && local == AnchorOnboardingStatus.IN_PROGRESS) {
            return local;
        }
        // Doc-verification approval pushes COMPLETED while LOS status becomes SANCTIONED.
        // Never regress a completed onboarding back to "under review" from that mapping.
        if (local == AnchorOnboardingStatus.COMPLETED
                && fromLos == AnchorOnboardingStatus.SUBMITTED) {
            return local;
        }
        if (local != fromLos) {
            anchor.setOnboardingStatus(fromLos);
            try {
                anchorRepository.save(anchor);
            } catch (Exception e) {
                log.warn("Could not persist reconciled onboardingStatus={} for {}: {}",
                        fromLos, anchor.getId(), e.getMessage());
            }
        }
        return fromLos;
    }

    private static AnchorOnboardingStatus mapLosStatus(String losStatus) {
        if (losStatus == null || losStatus.isBlank()) {
            return null;
        }
        return switch (losStatus.trim().toUpperCase()) {
            case "ANCHOR_CONSENT_PENDING" -> AnchorOnboardingStatus.INVITED;
            case "ANCHOR_SENT_BACK", "DOC_VERIFICATION_SENT_BACK" -> AnchorOnboardingStatus.SENT_BACK;
            case "ANCHOR_SUBMITTED", "DOC_VERIFICATION_PENDING", "PENDING_CREDIT_OFFICER",
                    "SENT_BACK_TO_RM", "KYC_IN_PROGRESS", "KYC_FAILED", "KYC_VERIFIED",
                    "UNDERWRITING", "PENDING_SANCTION" -> AnchorOnboardingStatus.SUBMITTED;
            // Anchor onboarding completes when ops approve document verification (LOS → SANCTIONED
            // and PLP onboardingStatus COMPLETED). Later lifecycle statuses stay unlocked.
            case "SANCTIONED", "KFS_GENERATED", "SANCTION_ISSUED", "ESIGN_PENDING", "ESIGN_COMPLETED",
                    "READY_FOR_DISBURSEMENT", "DISBURSEMENT_PENDING", "DISBURSED", "CLOSED", "ACTIVE"
                    -> AnchorOnboardingStatus.COMPLETED;
            default -> null;
        };
    }

    private static String friendlyStage(AnchorOnboardingStatus status, String losStatus) {
        if ("DOC_VERIFICATION_SENT_BACK".equalsIgnoreCase(losStatus)) {
            return "Action needed: signed documents were sent back for corrections";
        }
        if ("DOC_VERIFICATION_PENDING".equalsIgnoreCase(losStatus)) {
            return "Your signed documents are being verified by operations";
        }
        if ("ANCHOR_SUBMITTED".equalsIgnoreCase(losStatus)) {
            return "Your application has been submitted and is under review";
        }
        if ("SANCTIONED".equalsIgnoreCase(losStatus)
                || status == AnchorOnboardingStatus.COMPLETED) {
            return FRIENDLY_STAGE.get(AnchorOnboardingStatus.COMPLETED);
        }
        if (status == null) {
            return "Onboarding not started";
        }
        return FRIENDLY_STAGE.getOrDefault(status, status.name());
    }

    private static boolean canResume(AnchorOnboardingStatus status, String losStatus) {
        if ("DOC_VERIFICATION_SENT_BACK".equalsIgnoreCase(losStatus)
                || "ANCHOR_SENT_BACK".equalsIgnoreCase(losStatus)
                || "ANCHOR_CONSENT_PENDING".equalsIgnoreCase(losStatus)) {
            return true;
        }
        return status == AnchorOnboardingStatus.INVITED
                || status == AnchorOnboardingStatus.IN_PROGRESS
                || status == AnchorOnboardingStatus.SENT_BACK;
    }

    private static boolean forceOnboarding(AnchorOnboardingStatus status, String losStatus) {
        return canResume(status, losStatus);
    }

    private static boolean menusUnlocked(AnchorOnboardingStatus status) {
        return status == null || status == AnchorOnboardingStatus.COMPLETED;
    }

    private static String sendBackNotes(Map<String, Object> losApplication) {
        if (losApplication == null) {
            return null;
        }
        String anchorNotes = asString(losApplication.get("anchorSentBackNotes"));
        if (anchorNotes != null && !anchorNotes.isBlank()) {
            return anchorNotes;
        }
        String docNotes = asString(losApplication.get("docVerificationNotes"));
        if (docNotes != null && !docNotes.isBlank()) {
            return docNotes;
        }
        return null;
    }

    private static List<String> requiredActions(AnchorOnboardingStatus status, String losStatus) {
        List<String> actions = new ArrayList<>();
        if ("DOC_VERIFICATION_SENT_BACK".equalsIgnoreCase(losStatus)) {
            actions.add("Review the document verification notes");
            actions.add("Update documents or details and resubmit");
            return actions;
        }
        if (status == null || status == AnchorOnboardingStatus.INVITED) {
            actions.add("Complete corporate, documents, KYC, and consent sections");
            actions.add("Submit your application for review");
        } else if (status == AnchorOnboardingStatus.IN_PROGRESS) {
            actions.add("Finish the remaining onboarding sections");
            actions.add("Submit your application for review");
        } else if (status == AnchorOnboardingStatus.SENT_BACK) {
            actions.add("Review the notes from your relationship manager");
            actions.add("Update the requested details and resubmit");
        } else if (status == AnchorOnboardingStatus.SUBMITTED) {
            actions.add("Track progress on My application");
        }
        return actions;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
