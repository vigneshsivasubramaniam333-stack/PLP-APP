package com.plp.notification.consumer;

import com.plp.notification.config.RabbitMQConfig;
import com.plp.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoanEventConsumer {

    private final NotificationService notificationService;

    @RabbitListener(queues = RabbitMQConfig.LOAN_EVENT_QUEUE)
    public void handleLoanEvent(Map<String, Object> event) {
        try {
            String eventType = (String) event.get("eventType");
            String loanNumber = (String) event.get("loanNumber");
            String borrowerName = (String) event.getOrDefault("borrowerName", "Borrower");
            String borrowerEmail = (String) event.get("borrowerEmail");
            UUID borrowerId = event.get("borrowerId") != null ? UUID.fromString(event.get("borrowerId").toString()) : null;
            UUID loanId = event.get("loanId") != null ? UUID.fromString(event.get("loanId").toString()) : null;
            UUID invoiceId = event.get("invoiceId") != null ? UUID.fromString(event.get("invoiceId").toString()) : null;
            String amount = event.getOrDefault("amount", "0").toString();
            String dueDate = (String) event.getOrDefault("dueDate", "");
            String outstanding = event.getOrDefault("outstanding", "0").toString();

            if (borrowerId == null) {
                log.warn("Skipping notification — missing borrowerId for event: {}", eventType);
                return;
            }

            Map<String, String> vars = new HashMap<>();
            vars.put("borrowerName", borrowerName);
            vars.put("loanNumber", loanNumber);
            vars.put("amount", amount);
            vars.put("dueDate", dueDate);
            vars.put("outstanding", outstanding);
            vars.put("invoiceNumber", stringOrEmpty(event.get("invoiceNumber")));
            vars.put("vendorName", stringOrEmpty(event.get("vendorName")));
            vars.put("amountDue", stringOrEmpty(event.getOrDefault("amountDue", outstanding)));
            vars.put("paymentUrl", stringOrEmpty(event.get("paymentUrl")));

            String templateCode = mapEventToTemplate(eventType);
            if (templateCode != null) {
                boolean skipIfSentToday = "INVOICE_DUE_DATE_REMINDER_DAILY".equals(eventType);
                String referenceType = invoiceId != null ? "INVOICE" : "LOAN";
                UUID referenceId = invoiceId != null ? invoiceId : loanId;
                notificationService.sendNotificationForEvent(
                        eventType,
                        templateCode,
                        borrowerId,
                        borrowerEmail,
                        null,
                        vars,
                        referenceType,
                        referenceId,
                        skipIfSentToday);
                log.info("Notification processed for event {} loan {}", eventType, loanNumber);
            }
        } catch (Exception e) {
            log.error("Failed to process loan event: {}", e.getMessage(), e);
        }
    }

    private static String stringOrEmpty(Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    private String mapEventToTemplate(String eventType) {
        return switch (eventType) {
            case "LOAN_REQUESTED" -> "LOAN_REQUESTED";
            case "LOAN_APPROVED" -> "LOAN_APPROVED";
            case "LOAN_DISBURSED" -> "LOAN_DISBURSED";
            case "REPAYMENT_DUE" -> "REPAYMENT_DUE";
            case "REPAYMENT_RECEIVED" -> "REPAYMENT_RECEIVED";
            case "INVOICE_DISBURSED_NOTIFICATION", "INVOICE_DUE_DATE_REMINDER_DAILY" -> "INVOICE_DUE_DATE_REMINDER";
            default -> {
                log.warn("No template mapping for event: {}", eventType);
                yield null;
            }
        };
    }
}
