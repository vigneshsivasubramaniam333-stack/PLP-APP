package com.plp.program.service;

import com.plp.program.model.enums.ProductType;
import com.plp.program.model.entity.Borrower;
import com.plp.program.model.entity.Program;
import com.plp.program.model.entity.SubProgram;
import com.plp.program.model.entity.SubProgramBorrower;
import com.plp.program.repository.AnchorRepository;
import com.plp.program.repository.BorrowerRepository;
import com.plp.program.repository.ProgramRepository;
import com.plp.program.repository.SubProgramBorrowerRepository;
import com.plp.program.repository.SubProgramRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubProgramService {

    private static final String FLOW_PURCHASE_BILL_DISCOUNTING = "PURCHASE_BILL_DISCOUNTING";
    private static final String FLOW_SALES_BILL_DISCOUNTING = "SALES_BILL_DISCOUNTING";
    private static final String FLOW_PAY_LOAN = "PAY_LOAN";
    private static final String FLOW_PAY_DAY_LOAN = "PAY_DAY_LOAN";
    private static final String ROLE_SELLER = "SELLER";
    private static final String ROLE_BUYER = "BUYER";
    private static final String ROLE_EMPLOYER = "EMPLOYER";
    private static final String ROLE_EMPLOYEE = "EMPLOYEE";

    private final SubProgramRepository subProgramRepository;
    private final SubProgramBorrowerRepository subProgramBorrowerRepository;
    private final ProgramRepository programRepository;
    private final AnchorRepository anchorRepository;
    private final BorrowerRepository borrowerRepository;

    @Transactional
    public SubProgram createSubProgram(SubProgram subProgram) {
        if (subProgram.getCode() == null || subProgram.getCode().isBlank()) {
            subProgram.setCode(generateUniqueSubProgramCode());
        } else {
            subProgram.setCode(subProgram.getCode().trim());
        }
        if (subProgramRepository.existsByCode(subProgram.getCode())) {
            throw new RuntimeException("Sub program code already exists: " + subProgram.getCode());
        }
        Program program = programRepository.findById(subProgram.getProgramId())
                .orElseThrow(() -> new RuntimeException("Program not found: " + subProgram.getProgramId()));
        anchorRepository.findById(subProgram.getAnchorId())
                .orElseThrow(() -> new RuntimeException("Anchor not found: " + subProgram.getAnchorId()));

        // Program may act as an umbrella across multiple anchors; anchor is controlled at sub-program level.

        String flowType = normalizeToken(subProgram.getFlowType());
        if (program.getProductType() == ProductType.PAY_DAY_LOAN && FLOW_PAY_DAY_LOAN.equals(flowType)) {
            flowType = FLOW_PAY_LOAN;
        }
        subProgram.setFlowType(flowType);
        subProgram.setAnchorRole(normalizeToken(subProgram.getAnchorRole()));
        subProgram.setBorrowerRole(normalizeToken(subProgram.getBorrowerRole()));

        validateSubProgramForProgram(program, subProgram);

        if (subProgram.getLenderId() == null) {
            subProgram.setLenderId(program.getLenderId());
        }

        if (subProgram.getStatus() == null || subProgram.getStatus().isBlank()) {
            subProgram.setStatus("DRAFT");
        }

        applySubProgramLimitDefaults(subProgram);
        if (subProgram.getSubProgramLimit() == null
                || subProgram.getSubProgramLimit().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("subProgramLimit is required and must be greater than zero");
        }

        SubProgram saved = subProgramRepository.save(subProgram);
        log.info("Sub program created: {} ({})", saved.getCode(), saved.getId());
        return saved;
    }

    public List<SubProgram> listAll() {
        return subProgramRepository.findAll();
    }

    /** Sub-programs where {@code borrowerId} appears in {@code sub_program_borrowers}. */
    public List<SubProgram> listSubProgramsForAnchor(UUID anchorId) {
        return subProgramRepository.findByAnchorId(anchorId);
    }

    public List<SubProgram> listSubProgramsForBorrower(UUID borrowerId) {
        List<SubProgramBorrower> links = subProgramBorrowerRepository.findByBorrowerId(borrowerId);
        if (links.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = links.stream().map(SubProgramBorrower::getSubProgramId).distinct().toList();
        return new ArrayList<>(subProgramRepository.findAllById(ids));
    }

    public List<SubProgram> listByProgramId(UUID programId) {
        programRepository.findById(programId)
                .orElseThrow(() -> new RuntimeException("Program not found: " + programId));
        return subProgramRepository.findByProgramId(programId);
    }

    private String generateUniqueSubProgramCode() {
        String datePart = java.time.LocalDate.now().toString().replace("-", "");
        for (int i = 0; i < 25; i++) {
            String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
            String candidate = ("SP-" + datePart + "-" + suffix);
            if (candidate.length() > 50) {
                candidate = candidate.substring(0, 50);
            }
            if (!subProgramRepository.existsByCode(candidate)) {
                return candidate;
            }
        }
        throw new RuntimeException("Unable to generate a unique sub-program code");
    }

    public SubProgram getSubProgram(UUID id) {
        return subProgramRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sub program not found: " + id));
    }

    /**
     * Approves a draft sub-program (sets status ACTIVE).
     */
    @Transactional
    public SubProgram approveSubProgram(UUID id) {
        SubProgram sp = getSubProgram(id);
        if (!"DRAFT".equals(sp.getStatus())) {
            throw new RuntimeException("Only sub-programs in DRAFT status can be approved");
        }
        sp.setStatus("ACTIVE");
        SubProgram saved = subProgramRepository.save(sp);
        log.info("Sub program approved: {} ({})", saved.getCode(), saved.getId());
        return saved;
    }

    /**
     * Deactivates an active sub-program (sets status INACTIVE).
     */
    @Transactional
    public SubProgram deactivateSubProgram(UUID id) {
        SubProgram sp = getSubProgram(id);
        if (!"ACTIVE".equals(sp.getStatus())) {
            throw new RuntimeException("Only ACTIVE sub-programs can be deactivated");
        }
        sp.setStatus("INACTIVE");
        SubProgram saved = subProgramRepository.save(sp);
        log.info("Sub program deactivated: {} ({})", saved.getCode(), saved.getId());
        return saved;
    }

    @Transactional
    public SubProgramBorrower addBorrower(UUID subProgramId, SubProgramBorrower membership) {
        SubProgram subProgram = getSubProgram(subProgramId);
        membership.setSubProgramId(subProgram.getId());

        Borrower borrower = borrowerRepository.findById(membership.getBorrowerId())
                .orElseThrow(() -> new RuntimeException("Borrower not found: " + membership.getBorrowerId()));

        if (!borrower.getProgramId().equals(subProgram.getProgramId())) {
            throw new RuntimeException("Borrower belongs to a different program than this sub program");
        }

        if (subProgramBorrowerRepository.findBySubProgramIdAndBorrowerId(subProgramId, membership.getBorrowerId()).isPresent()) {
            throw new RuntimeException("Borrower already enrolled in this sub program");
        }

        applyBorrowerLimitDefaults(membership);

        SubProgramBorrower saved = subProgramBorrowerRepository.save(membership);
        log.info("Sub program borrower enrolled: subProgram={} borrower={}", subProgramId, membership.getBorrowerId());
        return saved;
    }

    public List<SubProgramBorrower> listBorrowers(UUID subProgramId) {
        getSubProgram(subProgramId);
        return subProgramBorrowerRepository.findBySubProgramId(subProgramId);
    }

    private static String normalizeToken(String s) {
        return s == null ? "" : s.trim().toUpperCase(Locale.ROOT);
    }

    private void validateSubProgramForProgram(Program program, SubProgram subProgram) {
        String flowType = subProgram.getFlowType();
        String anchorRole = subProgram.getAnchorRole();
        String borrowerRole = subProgram.getBorrowerRole();

        if (program.getProductType() == ProductType.PAY_DAY_LOAN) {
            if (!FLOW_PAY_LOAN.equals(flowType)) {
                throw new RuntimeException(
                        "For PAY_DAY_LOAN programs, flow_type must be PAY_LOAN or PAY_DAY_LOAN");
            }
            if (!ROLE_EMPLOYER.equals(anchorRole) || !ROLE_EMPLOYEE.equals(borrowerRole)) {
                throw new RuntimeException(
                        "For PAY_DAY_LOAN, anchor_role must be EMPLOYER and borrower_role must be EMPLOYEE");
            }
            return;
        }

        if (program.getProductType() == ProductType.INVOICE_DISCOUNTING) {
            validateInvoiceDiscountingFlowRoles(flowType, anchorRole, borrowerRole);
            return;
        }

        throw new RuntimeException(
                "Unsupported program product type for sub-program: " + program.getProductType());
    }

    private static void validateInvoiceDiscountingFlowRoles(String flowType, String anchorRole, String borrowerRole) {
        if (FLOW_PURCHASE_BILL_DISCOUNTING.equals(flowType)) {
            if (!ROLE_SELLER.equals(anchorRole) || !ROLE_BUYER.equals(borrowerRole)) {
                throw new RuntimeException(
                        "For PURCHASE_BILL_DISCOUNTING, anchor_role must be SELLER and borrower_role must be BUYER");
            }
            return;
        }
        if (FLOW_SALES_BILL_DISCOUNTING.equals(flowType)) {
            if (!ROLE_BUYER.equals(anchorRole) || !ROLE_SELLER.equals(borrowerRole)) {
                throw new RuntimeException(
                        "For SALES_BILL_DISCOUNTING, anchor_role must be BUYER and borrower_role must be SELLER");
            }
            return;
        }
        throw new RuntimeException(
                "Unsupported flow_type for invoice discounting: "
                        + flowType
                        + ". Use PURCHASE_BILL_DISCOUNTING or SALES_BILL_DISCOUNTING");
    }

    private static void applySubProgramLimitDefaults(SubProgram subProgram) {
        if (subProgram.getUtilizedLimit() == null) {
            subProgram.setUtilizedLimit(BigDecimal.ZERO);
        }
        if (subProgram.getAvailableLimit() == null && subProgram.getSubProgramLimit() != null) {
            subProgram.setAvailableLimit(subProgram.getSubProgramLimit());
        }
    }

    private static void applyBorrowerLimitDefaults(SubProgramBorrower membership) {
        if (membership.getUtilizedLimit() == null) {
            membership.setUtilizedLimit(BigDecimal.ZERO);
        }
        if (membership.getAvailableLimit() == null && membership.getBorrowerLimit() != null) {
            membership.setAvailableLimit(membership.getBorrowerLimit());
        }
    }
}
