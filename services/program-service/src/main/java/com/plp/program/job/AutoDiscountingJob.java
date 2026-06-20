package com.plp.program.job;

import com.plp.program.model.entity.Program;
import com.plp.program.repository.InvoiceRepository;
import com.plp.program.repository.ProgramRepository;
import com.plp.program.validation.ProgramParametersValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Initial auto-discounting scheduler: logs eligible programs/invoices on configured discounting day.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoDiscountingJob {

    private final ProgramRepository programRepository;
    private final InvoiceRepository invoiceRepository;

    @Scheduled(cron = "0 0 6 * * *")
    public void runDailyAutoDiscountingScan() {
        int dayOfMonth = LocalDate.now().getDayOfMonth();
        List<Program> programs = programRepository.findAll();
        for (Program program : programs) {
            Map<String, Object> params = program.getParameters();
            if (params == null) {
                continue;
            }
            try {
                params = ProgramParametersValidator.validateAndNormalize(params, program.getProductType());
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (!ProgramParametersValidator.parseYesNo(params.get("autoDiscounting"), false)) {
                continue;
            }
            int discountingDay = params.get("discountingDay") instanceof Number n ? n.intValue() : 0;
            if (discountingDay != dayOfMonth) {
                continue;
            }
            long eligibleCount = invoiceRepository.findByProgramId(program.getId()).stream()
                    .filter(inv -> "ELIGIBLE".equals(inv.getStatus())
                            || "BORROWER_ACCEPTED".equals(inv.getStatus())
                            || "PARTIALLY_DISCOUNTED".equals(inv.getStatus()))
                    .count();
            log.info(
                    "Auto-discounting scan program={} discountingDay={} eligibleInvoices={}",
                    program.getProgramCode(),
                    discountingDay,
                    eligibleCount);
        }
    }
}
