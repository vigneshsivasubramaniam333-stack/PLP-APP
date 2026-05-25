package com.plp.program.service;

import com.plp.program.integration.los.LosIntegrationResourceTypes;
import com.plp.program.model.dto.integration.LosBorrowerUpsertRequest;
import com.plp.program.model.dto.integration.LosBorrowerUpsertResponse;
import com.plp.program.model.dto.integration.LosBorrowerUpsertRequest.LosBorrowerPayload;
import com.plp.program.model.entity.Borrower;
import com.plp.program.model.enums.BorrowerStatus;
import com.plp.program.repository.AnchorRepository;
import com.plp.program.repository.BorrowerRepository;
import com.plp.program.integration.iam.IamUserProvisioner;
import com.plp.program.repository.ProgramRepository;
import com.plp.program.service.audit.LosSyncAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LosBorrowerIntegrationService {

    private final BorrowerRepository borrowerRepository;
    private final ProgramRepository programRepository;
    private final AnchorRepository anchorRepository;
    private final LosSyncAuditService losSyncAuditService;
    private final IamUserProvisioner iamUserProvisioner;

    @Transactional
    public LosBorrowerUpsertResponse upsert(LosBorrowerUpsertRequest req) {
        String sourceSystem = normalize(req.getSourceSystem());
        String extKey = "losBorrower:" + normalize(req.getLosBorrowerId());
        try {
            LosBorrowerUpsertResponse response = upsertInternal(req, sourceSystem);
            losSyncAuditService.recordSuccess(
                    LosIntegrationResourceTypes.BORROWER,
                    response.getPlpBorrowerId(),
                    sourceSystem,
                    extKey,
                    req,
                    response);
            return response;
        } catch (RuntimeException e) {
            losSyncAuditService.recordFailure(
                    LosIntegrationResourceTypes.BORROWER,
                    null,
                    sourceSystem,
                    extKey,
                    req,
                    e.getMessage());
            throw e;
        }
    }

    private LosBorrowerUpsertResponse upsertInternal(LosBorrowerUpsertRequest req, String sourceSystem) {
        String losBorrowerId = normalize(req.getLosBorrowerId());
        UUID programId = req.getPlpProgramId();
        UUID anchorId = req.getPlpAnchorId();

        programRepository.findById(programId).orElseThrow(() -> new RuntimeException("Program not found"));
        anchorRepository.findById(anchorId).orElseThrow(() -> new RuntimeException("Anchor not found"));

        Optional<Borrower> byLos =
                borrowerRepository.findBySourceSystemAndLosBorrowerId(sourceSystem, losBorrowerId);
        if (byLos.isPresent()) {
            Borrower b = byLos.get();
            if (!b.getProgramId().equals(programId)) {
                throw new RuntimeException("LOS borrower already exists under a different program");
            }
            if (!b.getAnchorId().equals(anchorId)) {
                throw new RuntimeException("Existing borrower anchor does not match requested anchor");
            }
            applyBorrowerDetails(b, req.getBorrower());
            borrowerRepository.save(b);
            provisionBorrowerPortalUser(b);
            return LosBorrowerUpsertResponse.builder()
                    .plpBorrowerId(b.getId())
                    .borrowerCode(b.getBorrowerCode())
                    .created(false)
                    .updated(true)
                    .build();
        }

        String borrowerCode = deterministicBorrowerCode(sourceSystem, losBorrowerId);
        if (borrowerRepository.findByBorrowerCode(borrowerCode).isPresent()) {
            borrowerCode = fallbackBorrowerCode();
        }

        Borrower borrower =
                Borrower.builder()
                        .borrowerCode(borrowerCode)
                        .name(req.getBorrower().getName().trim())
                        .email(trimOrNull(req.getBorrower().getEmail()))
                        .phone(trimOrNull(req.getBorrower().getPhone()))
                        .pan(trimOrNull(req.getBorrower().getPan()))
                        .gstin(trimOrNull(req.getBorrower().getGstin()))
                        .programId(programId)
                        .anchorId(anchorId)
                        .sourceSystem(sourceSystem)
                        .losBorrowerId(losBorrowerId)
                        .status(BorrowerStatus.PENDING_KYC)
                        .build();
        Borrower saved = borrowerRepository.save(borrower);
        log.info("LOS borrower created via integration: {} ({})", saved.getBorrowerCode(), saved.getId());
        provisionBorrowerPortalUser(saved);
        return LosBorrowerUpsertResponse.builder()
                .plpBorrowerId(saved.getId())
                .borrowerCode(saved.getBorrowerCode())
                .created(true)
                .updated(false)
                .build();
    }

    private static void applyBorrowerDetails(Borrower borrower, LosBorrowerPayload dto) {
        borrower.setName(dto.getName().trim());
        borrower.setEmail(trimOrNull(dto.getEmail()));
        borrower.setPhone(trimOrNull(dto.getPhone()));
        borrower.setPan(trimOrNull(dto.getPan()));
        borrower.setGstin(trimOrNull(dto.getGstin()));
    }

    private static String deterministicBorrowerCode(String sourceSystem, String losBorrowerId) {
        UUID nid =
                UUID.nameUUIDFromBytes((sourceSystem + "|" + losBorrowerId).getBytes(StandardCharsets.UTF_8));
        String suffix = nid.toString().replace("-", "").substring(0, 12).toUpperCase();
        return "BR-" + suffix;
    }

    private String fallbackBorrowerCode() {
        for (int i = 0; i < 25; i++) {
            String candidate = "BR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
            if (borrowerRepository.findByBorrowerCode(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new RuntimeException("Unable to generate a unique borrower code");
    }

    private static String normalize(String raw) {
        return raw == null ? "" : raw.trim();
    }

    private static String trimOrNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }

    private void provisionBorrowerPortalUser(Borrower borrower) {
        iamUserProvisioner.provisionBorrowerUser(
                borrower.getId(), borrower.getEmail(), borrower.getName(), borrower.getPhone());
    }
}
