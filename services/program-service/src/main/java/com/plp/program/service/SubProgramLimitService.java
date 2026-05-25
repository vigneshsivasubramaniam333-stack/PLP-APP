package com.plp.program.service;

import com.plp.program.model.entity.SubProgram;
import com.plp.program.model.entity.SubProgramBorrower;
import com.plp.program.repository.SubProgramBorrowerRepository;
import com.plp.program.repository.SubProgramRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubProgramLimitService {

    static final String INSUFFICIENT_AVAILABLE = "Requested amount exceeds sub-program available limit";

    private final SubProgramRepository subProgramRepository;
    private final SubProgramBorrowerRepository subProgramBorrowerRepository;

    public Map<String, Object> getSubProgramLimitSummary(UUID subProgramId) {
        SubProgram sub = subProgramRepository.findById(subProgramId)
                .orElseThrow(() -> new RuntimeException("Sub program not found: " + subProgramId));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("subProgramId", sub.getId());
        m.put("subProgramLimit", sub.getSubProgramLimit());
        m.put("utilizedLimit", nvl(sub.getUtilizedLimit()));
        m.put("availableLimit", sub.getAvailableLimit());
        return m;
    }

    public Map<String, Object> getBorrowerLimitSummary(UUID subProgramId, UUID borrowerId) {
        getSubProgram(subProgramId);
        SubProgramBorrower row = subProgramBorrowerRepository
                .findBySubProgramIdAndBorrowerId(subProgramId, borrowerId)
                .orElseThrow(() -> new RuntimeException("Borrower not enrolled in sub program: " + borrowerId));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("subProgramId", subProgramId);
        m.put("borrowerId", borrowerId);
        m.put("borrowerLimit", row.getBorrowerLimit());
        m.put("utilizedLimit", nvl(row.getUtilizedLimit()));
        m.put("availableLimit", row.getAvailableLimit());
        return m;
    }

    @Transactional
    public Map<String, Object> blockLimits(UUID subProgramId, UUID borrowerId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Amount must be positive");
        }

        SubProgram sub = subProgramRepository.findByIdForUpdate(subProgramId)
                .orElseThrow(() -> new RuntimeException("Sub program not found: " + subProgramId));
        SubProgramBorrower membership = subProgramBorrowerRepository
                .findBySubProgramIdAndBorrowerIdForUpdate(subProgramId, borrowerId)
                .orElseThrow(() -> new RuntimeException("Borrower not enrolled in sub program: " + borrowerId));

        BigDecimal subAvail = effectiveAvailable(
                sub.getAvailableLimit(), sub.getSubProgramLimit(), sub.getUtilizedLimit());
        BigDecimal memAvail = effectiveAvailable(
                membership.getAvailableLimit(), membership.getBorrowerLimit(), membership.getUtilizedLimit());

        if (amount.compareTo(subAvail) > 0 || amount.compareTo(memAvail) > 0) {
            throw new RuntimeException(INSUFFICIENT_AVAILABLE);
        }

        BigDecimal subU = nvl(sub.getUtilizedLimit());
        BigDecimal subA = sub.getAvailableLimit() != null ? sub.getAvailableLimit() : subAvail;
        sub.setUtilizedLimit(subU.add(amount));
        sub.setAvailableLimit(subA.subtract(amount));

        BigDecimal memU = nvl(membership.getUtilizedLimit());
        BigDecimal memA = membership.getAvailableLimit() != null ? membership.getAvailableLimit() : memAvail;
        membership.setUtilizedLimit(memU.add(amount));
        membership.setAvailableLimit(memA.subtract(amount));

        subProgramRepository.save(sub);
        subProgramBorrowerRepository.save(membership);

        log.info("Sub-program limits blocked: subProgram={} borrower={} amount={}", subProgramId, borrowerId, amount);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("subProgram", summarizeSub(sub));
        out.put("borrower", summarizeBorrower(subProgramId, membership));
        return out;
    }

    @Transactional
    public Map<String, Object> releaseLimits(UUID subProgramId, UUID borrowerId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Amount must be positive");
        }

        SubProgram sub = subProgramRepository.findByIdForUpdate(subProgramId)
                .orElseThrow(() -> new RuntimeException("Sub program not found: " + subProgramId));
        SubProgramBorrower membership = subProgramBorrowerRepository
                .findBySubProgramIdAndBorrowerIdForUpdate(subProgramId, borrowerId)
                .orElseThrow(() -> new RuntimeException("Borrower not enrolled in sub program: " + borrowerId));

        BigDecimal subCap = sub.getSubProgramLimit();
        BigDecimal memCap = membership.getBorrowerLimit();

        BigDecimal subUtilized = nvl(sub.getUtilizedLimit());
        BigDecimal subAvail = nvl(sub.getAvailableLimit());
        BigDecimal newSubUtilized = subUtilized.subtract(amount);
        if (newSubUtilized.compareTo(BigDecimal.ZERO) < 0) {
            newSubUtilized = BigDecimal.ZERO;
        }
        sub.setUtilizedLimit(newSubUtilized);
        BigDecimal newSubAvail = subAvail.add(amount);
        if (subCap != null) {
            newSubAvail = newSubAvail.min(subCap);
        }
        sub.setAvailableLimit(newSubAvail);

        BigDecimal memUtilized = nvl(membership.getUtilizedLimit());
        BigDecimal memAvail = nvl(membership.getAvailableLimit());
        BigDecimal newMemUtilized = memUtilized.subtract(amount);
        if (newMemUtilized.compareTo(BigDecimal.ZERO) < 0) {
            newMemUtilized = BigDecimal.ZERO;
        }
        membership.setUtilizedLimit(newMemUtilized);
        BigDecimal newMemAvail = memAvail.add(amount);
        if (memCap != null) {
            newMemAvail = newMemAvail.min(memCap);
        }
        membership.setAvailableLimit(newMemAvail);

        subProgramRepository.save(sub);
        subProgramBorrowerRepository.save(membership);

        log.info("Sub-program limits released: subProgram={} borrower={} amount={}", subProgramId, borrowerId, amount);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("subProgram", summarizeSub(sub));
        out.put("borrower", summarizeBorrower(subProgramId, membership));
        return out;
    }

    private SubProgram getSubProgram(UUID id) {
        return subProgramRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sub program not found: " + id));
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    /**
     * Effective headroom for block checks. When all three primitives are present, trust at most the tighter of stored
     * {@code availableLimit} and {@code cap - utilized}; otherwise derive from {@code cap - utilized}.
     */
    private static BigDecimal effectiveAvailable(BigDecimal availableLimit, BigDecimal cap, BigDecimal utilized) {
        BigDecimal u = nvl(utilized);
        if (availableLimit != null && cap != null && utilized != null) {
            BigDecimal computed = cap.subtract(utilized);
            return availableLimit.min(computed).max(BigDecimal.ZERO);
        }
        if (cap != null) {
            return cap.subtract(u).max(BigDecimal.ZERO);
        }
        return BigDecimal.ZERO;
    }

    private static Map<String, Object> summarizeSub(SubProgram sub) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("subProgramId", sub.getId());
        m.put("subProgramLimit", sub.getSubProgramLimit());
        m.put("utilizedLimit", nvl(sub.getUtilizedLimit()));
        m.put("availableLimit", sub.getAvailableLimit());
        return m;
    }

    private static Map<String, Object> summarizeBorrower(UUID subProgramId, SubProgramBorrower row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("subProgramId", subProgramId);
        m.put("borrowerId", row.getBorrowerId());
        m.put("borrowerLimit", row.getBorrowerLimit());
        m.put("utilizedLimit", nvl(row.getUtilizedLimit()));
        m.put("availableLimit", row.getAvailableLimit());
        return m;
    }
}
