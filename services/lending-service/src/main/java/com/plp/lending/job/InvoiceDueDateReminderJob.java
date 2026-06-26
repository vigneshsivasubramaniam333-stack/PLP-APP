package com.plp.lending.job;

import com.plp.lending.service.InvoiceDueDateNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InvoiceDueDateReminderJob {

    private final InvoiceDueDateNotificationService invoiceDueDateNotificationService;

    /** Daily at 08:00 server time — sends invoice due-date reminders when enabled in admin notification settings. */
    @Scheduled(cron = "${plp.notification.invoice-due-reminder-cron:0 0 8 * * *}")
    public void sendDailyReminders() {
        try {
            invoiceDueDateNotificationService.sendDailyReminders();
        } catch (Exception e) {
            log.error("Invoice due-date reminder job failed: {}", e.getMessage(), e);
        }
    }
}
