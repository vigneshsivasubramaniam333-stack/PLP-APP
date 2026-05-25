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

            String templateCode = mapEventToTemplate(eventType);
            if (templateCode != null) {
                notificationService.sendNotification(templateCode, borrowerId, borrowerEmail, null,
                        vars, "LOAN", loanId);
                log.info("Notification sent for event {} loan {}", eventType, loanNumber);
            }
        } catch (Exception e) {
            log.error("Failed to process loan event: {}", e.getMessage(), e);
        }
    }

    private String mapEventToTemplate(String eventType) {
        return switch (eventType) {
            case "LOAN_REQUESTED" -> "LOAN_REQUESTED";
            case "LOAN_APPROVED" -> "LOAN_APPROVED";
            case "LOAN_DISBURSED" -> "LOAN_DISBURSED";
            case "REPAYMENT_DUE" -> "REPAYMENT_DUE";
            case "REPAYMENT_RECEIVED" -> "REPAYMENT_RECEIVED";
            default -> {
                log.warn("No template mapping for event: {}", eventType);
                yield null;
            }
        };
    }
}
