package com.plp.program.job;

import com.plp.program.service.earlypay.EarlyPayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EarlyPayNightlyRejectJob {

    private final EarlyPayService earlyPayService;

    /** Auto-reject pending Early Pay requests (legacy nightly cron). */
    @Scheduled(cron = "0 30 0 * * *")
    public void rejectPendingEarlyPayRequests() {
        log.info("Running nightly Early Pay pending-request rejection");
        earlyPayService.rejectAllPendingRequests();
    }
}
