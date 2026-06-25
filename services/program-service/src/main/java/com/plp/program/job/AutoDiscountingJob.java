package com.plp.program.job;

import com.plp.program.model.entity.Program;
import com.plp.program.repository.ProgramRepository;
import com.plp.program.service.InvoiceAutoFinanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AutoDiscountingJob {

    private final ProgramRepository programRepository;
    private final InvoiceAutoFinanceService invoiceAutoFinanceService;

    @Scheduled(cron = "0 0 6 * * *")
    public void runDailyAutoDiscountingScan() {
        LocalDate today = LocalDate.now();
        List<Program> programs = programRepository.findAll();
        int totalProcessed = 0;
        for (Program program : programs) {
            try {
                int n = invoiceAutoFinanceService.runAutoDiscountingForProgram(program, today);
                if (n > 0) {
                    log.info(
                            "Auto-discounting program={} discountingDay={} financeRequests={}",
                            program.getProgramCode(),
                            today.getDayOfMonth(),
                            n);
                }
                totalProcessed += n;
            } catch (Exception e) {
                log.error("Auto-discounting failed for program {}: {}", program.getProgramCode(), e.getMessage());
            }
        }
        if (totalProcessed > 0) {
            log.info("Auto-discounting job completed: {} finance request(s) submitted", totalProcessed);
        }
    }
}
