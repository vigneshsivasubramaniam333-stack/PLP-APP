package com.plp.program.service;

import com.plp.program.model.dto.EffectiveBorrowerTermsDto;
import com.plp.program.model.entity.SubProgram;
import com.plp.program.model.entity.SubProgramBorrower;
import com.plp.program.repository.SubProgramBorrowerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BorrowerPaymentProfileService {

    public static final String PAYMENT_METHOD_PAYU = "PAYU_PG";

    private final SubProgramBorrowerRepository subProgramBorrowerRepository;
    private final SubProgramService subProgramService;
    private final SubProgramBorrowerTermsResolver borrowerTermsResolver;

    public PaymentProfile buildProfile(UUID borrowerId) {
        List<SubProgramBorrower> links = subProgramBorrowerRepository.findByBorrowerId(borrowerId);
        String paymentMethod = "SMART_COLLECT";
        List<EnrollmentRow> enrollments = new ArrayList<>();
        for (SubProgramBorrower link : links) {
            if (!"ACTIVE".equalsIgnoreCase(link.getStatus())) {
                continue;
            }
            SubProgram subProgram = subProgramService.getSubProgram(link.getSubProgramId());
            EffectiveBorrowerTermsDto terms =
                    borrowerTermsResolver.resolveEffectiveTerms(link.getSubProgramId(), borrowerId);
            if (PAYMENT_METHOD_PAYU.equals(terms.getPaymentMethod())) {
                paymentMethod = PAYMENT_METHOD_PAYU;
            }
            enrollments.add(new EnrollmentRow(
                    link.getSubProgramId(),
                    subProgram.getProgramId(),
                    subProgram.getAnchorId(),
                    subProgram.getFlowType()));
        }
        return new PaymentProfile(paymentMethod, enrollments);
    }

    public Optional<UUID> resolveSubProgramId(UUID borrowerId, UUID programId, UUID anchorId) {
        if (borrowerId == null || programId == null) {
            return Optional.empty();
        }
        return buildProfile(borrowerId).enrollments().stream()
                .filter(row -> programId.equals(row.programId()))
                .filter(row -> anchorId == null || anchorId.equals(row.anchorId()))
                .map(EnrollmentRow::subProgramId)
                .findFirst();
    }

    public record PaymentProfile(String paymentMethod, List<EnrollmentRow> enrollments) {}

    public record EnrollmentRow(UUID subProgramId, UUID programId, UUID anchorId, String flowType) {}
}
