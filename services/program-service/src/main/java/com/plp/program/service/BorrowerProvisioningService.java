package com.plp.program.service;

import com.plp.program.model.dto.BorrowerCreateRequest;
import com.plp.program.model.entity.Borrower;
import com.plp.program.model.entity.SubProgram;
import com.plp.program.model.entity.SubProgramBorrower;
import com.plp.program.model.enums.BorrowerStatus;
import com.plp.program.repository.BorrowerRepository;
import com.plp.program.repository.SubProgramRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BorrowerProvisioningService {

    private final BorrowerRepository borrowerRepository;
    private final SubProgramRepository subProgramRepository;
    private final SubProgramService subProgramService;

    @Transactional
    public Borrower createBorrower(BorrowerCreateRequest req) {
        if (req.getName() == null || req.getName().isBlank()) {
            throw new RuntimeException("Borrower name is required");
        }
        String borrowerCode =
                req.getBorrowerCode() == null || req.getBorrowerCode().isBlank()
                        ? generateBorrowerCode()
                        : req.getBorrowerCode().trim();
        if (borrowerRepository.findByBorrowerCode(borrowerCode).isPresent()) {
            throw new RuntimeException("Borrower code already exists: " + borrowerCode);
        }

        BorrowerStatus status =
                req.getStatus() != null && !req.getStatus().isBlank()
                        ? BorrowerStatus.valueOf(req.getStatus().trim())
                        : BorrowerStatus.ACTIVE;

        Borrower borrower;
        if (req.getSubProgramId() != null) {
            SubProgram sp =
                    subProgramRepository
                            .findById(req.getSubProgramId())
                            .orElseThrow(() -> new RuntimeException("Sub-program not found: " + req.getSubProgramId()));
            if (req.getSubProgramBorrowerLimit() == null || req.getSubProgramBorrowerLimit().compareTo(BigDecimal.ZERO) < 0) {
                throw new RuntimeException("subProgramBorrowerLimit is required when creating with subProgramId");
            }
            borrower =
                    Borrower.builder()
                            .borrowerCode(borrowerCode)
                            .name(req.getName().trim())
                            .email(trimOrNull(req.getEmail()))
                            .phone(trimOrNull(req.getPhone()))
                            .programId(sp.getProgramId())
                            .anchorId(sp.getAnchorId())
                            .status(status)
                            .build();
            borrower = borrowerRepository.save(borrower);
            SubProgramBorrower membership =
                    SubProgramBorrower.builder()
                            .borrowerId(borrower.getId())
                            .borrowerLimit(req.getSubProgramBorrowerLimit())
                            .status("ACTIVE")
                            .build();
            subProgramService.addBorrower(sp.getId(), membership);
            return borrower;
        }

        if (req.getProgramId() == null || req.getAnchorId() == null) {
            throw new RuntimeException("programId and anchorId are required when subProgramId is omitted");
        }
        borrower =
                Borrower.builder()
                        .borrowerCode(borrowerCode)
                        .name(req.getName().trim())
                        .email(trimOrNull(req.getEmail()))
                        .phone(trimOrNull(req.getPhone()))
                        .programId(req.getProgramId())
                        .anchorId(req.getAnchorId())
                        .status(status)
                        .build();
        return borrowerRepository.save(borrower);
    }

    private static String trimOrNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }

    private String generateBorrowerCode() {
        for (int i = 0; i < 20; i++) {
            String candidate = "BR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
            if (borrowerRepository.findByBorrowerCode(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new RuntimeException("Unable to generate a unique borrower code");
    }
}
