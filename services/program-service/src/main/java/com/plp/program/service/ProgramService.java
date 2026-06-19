package com.plp.program.service;

import com.plp.program.model.dto.ProgramEditDto;
import com.plp.program.model.entity.Program;
import com.plp.program.model.enums.ProgramStatus;
import com.plp.program.repository.BorrowerLimitRepository;
import com.plp.program.repository.ProgramRepository;
import com.plp.program.repository.SubProgramRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final ProgramRepository programRepository;
    private final BorrowerLimitRepository borrowerLimitRepository;
    private final SubProgramRepository subProgramRepository;

    @Transactional
    public Program createProgram(Program program) {
        normalizeNewProgram(program);
        program = programRepository.save(program);
        log.info(
                "Program created: {} ({}) for product {}",
                program.getProgramName(),
                program.getProgramCode(),
                program.getProductType());
        return program;
    }

    private void normalizeNewProgram(Program program) {
        String code = program.getProgramCode();
        if (code == null || code.isBlank()) {
            program.setProgramCode(generateUniqueProgramCode());
        } else {
            program.setProgramCode(code.trim());
        }
        if (program.getAnchorLimit() == null) {
            program.setAnchorLimit(ZERO);
        }
        if (program.getProgramLimit() == null) {
            throw new RuntimeException("programLimit is required");
        }
        if (program.getLenderId() == null) {
            throw new RuntimeException("lenderId is required");
        }
        if (program.getMaxBorrowerLimit() == null) {
            throw new RuntimeException("maxBorrowerLimit is required");
        }
        if (program.getParameters() == null) {
            program.setParameters(defaultProgramParameters());
        } else {
            program.setParameters(mergeProgramParameters(defaultProgramParameters(), program.getParameters()));
        }
        if (program.getLmsEntryIn() == null || program.getLmsEntryIn().isBlank()) {
            program.setLmsEntryIn("NO");
        }
    }

    private static Map<String, Object> defaultProgramParameters() {
        Map<String, Object> p = new HashMap<>();
        p.put("enablePaymentForBorrower", false);
        p.put("autoDiscounting", false);
        p.put("discountingDay", 1);
        p.put("gapBetweenDiscountingDays", 0);
        return p;
    }

    private static Map<String, Object> mergeProgramParameters(Map<String, Object> defaults, Map<String, Object> incoming) {
        Map<String, Object> merged = new HashMap<>(defaults);
        merged.putAll(incoming);
        return merged;
    }

    private String generateUniqueProgramCode() {
        String day = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        for (int i = 0; i < 20; i++) {
            String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
            String candidate = "PRG-" + day + "-" + suffix;
            if (candidate.length() > 30) {
                candidate = candidate.substring(0, 30);
            }
            if (programRepository.findByProgramCode(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new RuntimeException("Unable to generate a unique program code");
    }

    public Program getProgram(UUID programId) {
        return programRepository
                .findById(programId)
                .orElseThrow(() -> new RuntimeException("Program not found: " + programId));
    }

    /** Include computed utilized / available from program-level borrower limits for API payloads. */
    public Program getProgramEnriched(UUID programId) {
        Program p = getProgram(programId);
        attachProgramLimitHeadroom(java.util.List.of(p));
        return p;
    }

    public Program getProgramByCode(String programCode) {
        return programRepository
                .findByProgramCode(programCode)
                .orElseThrow(() -> new RuntimeException("Program not found: " + programCode));
    }

    public Optional<Program> findOptionalByProgramCode(String programCode) {
        return programRepository.findByProgramCode(programCode);
    }

    /** Program rows with {@link Program#setUtilizedLimit} / {@link Program#setAvailableLimit} filled for API listing. */
    @Transactional(readOnly = true)
    public List<Program> listPrograms() {
        List<Program> programs = programRepository.findAll();
        attachProgramLimitHeadroom(programs);
        return programs;
    }

    /** Anchor portal: programs linked to anchor with computed utilized / available headroom. */
    @Transactional(readOnly = true)
    public List<Program> listProgramsForAnchor(UUID anchorId) {
        List<Program> programs = programRepository.findProgramsForAnchor(anchorId);
        attachProgramLimitHeadroom(programs);
        return programs;
    }

    private void attachProgramLimitHeadroom(List<Program> programs) {
        for (Program p : programs) {
            BigDecimal sum = resolveProgramUtilized(p.getId());
            BigDecimal cap = p.getProgramLimit() != null ? p.getProgramLimit() : ZERO;
            p.setUtilizedLimit(sum);
            p.setAvailableLimit(cap.subtract(sum).max(ZERO));
        }
    }

    /**
     * When a program has sub-programs, aggregate utilization from {@code sub_programs.utilized_limit}.
     * Legacy programs without sub-programs continue to use {@code borrower_limits}.
     */
    private BigDecimal resolveProgramUtilized(UUID programId) {
        if (subProgramRepository.countByProgramId(programId) > 0) {
            BigDecimal subSum = subProgramRepository.sumUtilizedByProgramId(programId);
            return subSum != null ? subSum : ZERO;
        }
        BigDecimal legacySum = borrowerLimitRepository.sumUtilizedByProgramId(programId);
        return legacySum != null ? legacySum : ZERO;
    }

    /**
     * Updates display name, description, and merges partial {@code config} JSON (null values ignored).
     */
    @Transactional
    public Program updateProgram(UUID programId, ProgramEditDto dto) {
        Program program = getProgram(programId);
        if (dto.getName() != null && !dto.getName().isBlank()) {
            program.setProgramName(dto.getName().trim());
        }
        if (dto.getDescription() != null) {
            String d = dto.getDescription().trim();
            program.setDescription(d.isEmpty() ? null : d);
        }
        if (dto.getMarginPercent() != null) {
            program.setMarginPercent(dto.getMarginPercent());
        }
        if (dto.getConfig() != null && !dto.getConfig().isEmpty()) {
            Map<String, Object> merged =
                    program.getConfig() == null ? new HashMap<>() : new HashMap<>(program.getConfig());
            for (Map.Entry<String, Object> e : dto.getConfig().entrySet()) {
                if (e.getValue() != null) {
                    merged.put(e.getKey(), e.getValue());
                }
            }
            program.setConfig(merged);
        }
        if (dto.getParameters() != null && !dto.getParameters().isEmpty()) {
            Map<String, Object> merged =
                    program.getParameters() == null ? new HashMap<>() : new HashMap<>(program.getParameters());
            for (Map.Entry<String, Object> e : dto.getParameters().entrySet()) {
                if (e.getValue() != null) {
                    merged.put(e.getKey(), e.getValue());
                }
            }
            program.setParameters(merged);
        }
        if (dto.getLmsEntryIn() != null && !dto.getLmsEntryIn().isBlank()) {
            String v = dto.getLmsEntryIn().trim().toUpperCase();
            if (!"YES".equals(v) && !"NO".equals(v)) {
                throw new RuntimeException("lmsEntryIn must be YES or NO");
            }
            program.setLmsEntryIn(v);
        }
        if (dto.getEncoreProductCode() != null) {
            String code = dto.getEncoreProductCode().trim();
            program.setEncoreProductCode(code.isEmpty() ? null : code);
        }
        Program saved = programRepository.save(program);
        log.info("Program {} metadata/config updated", saved.getProgramCode());
        return saved;
    }

    @Transactional
    public Program updateStatus(UUID programId, ProgramStatus newStatus) {
        Program program = getProgram(programId);
        ProgramStatus currentStatus = program.getStatus();
        validateStatusTransition(currentStatus, newStatus);
        program.setStatus(newStatus);
        programRepository.save(program);
        log.info("Program {} status changed: {} → {}", program.getProgramCode(), currentStatus, newStatus);
        return program;
    }

    public Map<String, Object> getUtilization(UUID programId) {
        Program program = getProgram(programId);
        BigDecimal totalUtilized = resolveProgramUtilized(programId);
        BigDecimal available = program.getProgramLimit().subtract(totalUtilized);
        BigDecimal utilizationPercent = totalUtilized
                .multiply(BigDecimal.valueOf(100))
                .divide(program.getProgramLimit(), 2, RoundingMode.HALF_UP);

        return Map.of(
                "programId", programId,
                "programLimit", program.getProgramLimit(),
                "totalUtilized", totalUtilized,
                "available", available,
                "utilizationPercent", utilizationPercent);
    }

    private void validateStatusTransition(ProgramStatus from, ProgramStatus to) {
        boolean valid = switch (from) {
            case DRAFT -> to == ProgramStatus.ACTIVE;
            case ACTIVE -> to == ProgramStatus.PAUSED || to == ProgramStatus.CLOSED;
            case PAUSED -> to == ProgramStatus.ACTIVE || to == ProgramStatus.CLOSED;
            case CLOSED -> false;
        };
        if (!valid) {
            throw new RuntimeException("Invalid status transition: " + from + " → " + to);
        }
    }
}
