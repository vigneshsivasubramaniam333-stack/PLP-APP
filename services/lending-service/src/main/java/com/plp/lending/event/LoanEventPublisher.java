package com.plp.lending.event;

import com.plp.lending.config.InvoiceNotificationProperties;
import com.plp.lending.config.RabbitMQConfig;
import com.plp.lending.integration.BorrowerContactClient;
import com.plp.lending.model.entity.Loan;
import com.plp.lending.service.InvoiceDueDateNotificationService.InvoiceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Async notifications for loan lifecycle. Domain event names are stable for integrations.
 * <p>{@code LOAN_APPROVED} is emitted when a loan enters {@link com.plp.lending.model.enums.LoanStatus#SANCTIONED}
 * (credit sanction), not on disbursement.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoanEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final BorrowerContactClient borrowerContactClient;
    private final InvoiceNotificationProperties notificationProperties;

    @Async
    public void publishLoanEvent(String eventType, Loan loan) {
        try {
            BorrowerContactClient.BorrowerContact contact =
                    borrowerContactClient.fetch(loan.getBorrowerId());
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", eventType);
            event.put("loanId", loan.getId().toString());
            event.put("loanNumber", loan.getLoanNumber());
            event.put("borrowerId", loan.getBorrowerId().toString());
            event.put("borrowerName", contact.name());
            event.put("borrowerEmail", contact.email());
            event.put("programId", loan.getProgramId() != null ? loan.getProgramId().toString() : null);
            event.put("anchorId", loan.getAnchorId() != null ? loan.getAnchorId().toString() : null);
            event.put("productType", loan.getProductType());
            event.put("amount", loan.getRequestedAmount() != null ? loan.getRequestedAmount().toString() : "0");
            event.put("status", loan.getStatus() != null ? loan.getStatus().name() : null);
            event.put("dueDate", loan.getDueDate() != null ? loan.getDueDate().toString() : "");
            event.put("outstanding", loan.getOutstandingAmount() != null ? loan.getOutstandingAmount().toString() : "0");

            rabbitTemplate.convertAndSend(RabbitMQConfig.LOAN_EVENT_EXCHANGE, "loan." + eventType.toLowerCase(), event);
            log.info("Published loan event: {} for {}", eventType, loan.getLoanNumber());
        } catch (Exception e) {
            log.error("Failed to publish loan event: {} - {}", eventType, e.getMessage());
        }
    }

    @Async
    public void publishInvoiceDueDateReminder(
            Loan loan,
            String eventType,
            BorrowerContactClient.BorrowerContact contact,
            InvoiceContext invoiceContext) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", eventType);
            event.put("loanId", loan.getId().toString());
            event.put("loanNumber", loan.getLoanNumber());
            event.put("borrowerId", loan.getBorrowerId().toString());
            event.put("borrowerName", contact.name());
            event.put("borrowerEmail", contact.email());
            event.put("invoiceId", invoiceContext.invoiceId().toString());
            event.put("invoiceNumber", invoiceContext.invoiceNumber());
            event.put("vendorName", invoiceContext.vendorName());
            event.put("dueDate", invoiceContext.dueDateLabel());
            event.put("amountDue", invoiceContext.amountDueLabel());
            event.put("outstanding", invoiceContext.amountDueLabel());
            event.put("amount", invoiceContext.amountDueLabel());
            event.put("paymentUrl", notificationProperties.invoicePaymentPath());
            event.put("productType", loan.getProductType());

            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.LOAN_EVENT_EXCHANGE, "loan." + eventType.toLowerCase(), event);
            log.info("Published invoice reminder event: {} for loan {}", eventType, loan.getLoanNumber());
        } catch (Exception e) {
            log.error("Failed to publish invoice reminder event {} for {}: {}", eventType, loan.getLoanNumber(),
                    e.getMessage());
        }
    }

    @Async
    public void publishAuditEvent(String entityType, String entityId, String action,
                                   String actorId, String actorRole, String oldValues, String newValues) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("entityType", entityType);
            event.put("entityId", entityId);
            event.put("action", action);
            event.put("actorId", actorId);
            event.put("actorRole", actorRole);
            event.put("oldValues", oldValues);
            event.put("newValues", newValues);

            rabbitTemplate.convertAndSend(RabbitMQConfig.AUDIT_EXCHANGE, "audit.loan", event);
            log.debug("Published audit event: {} {} {}", entityType, entityId, action);
        } catch (Exception e) {
            log.error("Failed to publish audit event: {}", e.getMessage());
        }
    }
}
