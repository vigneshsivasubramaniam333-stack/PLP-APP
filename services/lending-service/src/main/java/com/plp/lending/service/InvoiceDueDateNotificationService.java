package com.plp.lending.service;

import com.plp.lending.config.InvoiceNotificationProperties;
import com.plp.lending.event.LoanEventPublisher;
import com.plp.lending.integration.BorrowerContactClient;
import com.plp.lending.integration.ProgramServiceAuthHeaders;
import com.plp.lending.model.entity.Loan;
import com.plp.lending.model.enums.LoanStatus;
import com.plp.lending.repository.LoanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceDueDateNotificationService {

    private static final DateTimeFormatter DUE_DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private static final List<LoanStatus> OPEN_INVOICE_LOAN_STATUSES = List.of(
            LoanStatus.DISBURSED, LoanStatus.REPAYMENT_DUE, LoanStatus.OVERDUE);

    private final LoanRepository loanRepository;
    private final LoanEventPublisher loanEventPublisher;
    private final BorrowerContactClient borrowerContactClient;
    private final InvoiceNotificationProperties notificationProperties;
    private final RestTemplate restTemplate;

    public void notifyOnDisbursement(Loan loan) {
        if (!isInvoiceDiscountingLoan(loan)) {
            return;
        }
        publishReminder(loan, "INVOICE_DISBURSED_NOTIFICATION");
    }

    public void sendDailyReminders() {
        List<Loan> loans = loanRepository.findOpenInvoiceDiscountingLoans(OPEN_INVOICE_LOAN_STATUSES);
        int sent = 0;
        for (Loan loan : loans) {
            try {
                publishReminder(loan, "INVOICE_DUE_DATE_REMINDER_DAILY");
                sent++;
            } catch (Exception e) {
                log.warn("Daily invoice reminder failed for loan {}: {}", loan.getLoanNumber(), e.getMessage());
            }
        }
        if (sent > 0) {
            log.info("Daily invoice due-date reminder job queued {} notification(s)", sent);
        }
    }

    private void publishReminder(Loan loan, String eventType) {
        if (!isInvoiceDiscountingLoan(loan)) {
            return;
        }
        InvoiceContext ctx = resolveInvoiceContext(loan);
        if (ctx == null) {
            log.warn("Skipping invoice reminder for loan {} — invoice context unavailable", loan.getLoanNumber());
            return;
        }
        BorrowerContactClient.BorrowerContact contact = borrowerContactClient.fetch(loan.getBorrowerId());
        loanEventPublisher.publishInvoiceDueDateReminder(loan, eventType, contact, ctx);
    }

    private boolean isInvoiceDiscountingLoan(Loan loan) {
        return loan != null
                && "INVOICE_DISCOUNTING".equals(loan.getProductType())
                && loan.getInvoiceId() != null;
    }

    private InvoiceContext resolveInvoiceContext(Loan loan) {
        Map<String, Object> invoice = fetchInvoiceJson(loan.getInvoiceId());
        if (invoice == null || invoice.isEmpty()) {
            return null;
        }
        String invoiceNumber = stringField(invoice.get("invoiceNumber"));
        LocalDate dueDate = parseLocalDate(invoice.get("dueDate"));
        if (dueDate == null) {
            dueDate = loan.getDueDate();
        }
        String dueDateLabel = dueDate != null ? dueDate.format(DUE_DATE_FMT) : "";
        BigDecimal amountDue = loan.getOutstandingAmount() != null && loan.getOutstandingAmount().signum() > 0
                ? loan.getOutstandingAmount()
                : firstNonNullAmount(loan.getDisbursedAmount(), loan.getSanctionedAmount(), loan.getRequestedAmount());
        if (amountDue == null) {
            amountDue = parseBigDecimal(invoice.get("netAmount"));
        }
        String currency = stringField(invoice.get("currency"));
        if (currency.isBlank()) {
            currency = "INR";
        }
        String amountDueLabel = formatMoney(amountDue, currency);
        UUID anchorId = parseUuid(invoice.get("anchorId"));
        String vendorName = anchorId != null ? fetchAnchorName(anchorId) : "";
        return new InvoiceContext(
                loan.getInvoiceId(),
                invoiceNumber,
                vendorName,
                dueDateLabel,
                amountDueLabel);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchInvoiceJson(UUID invoiceId) {
        try {
            HttpEntity<Void> entity = new HttpEntity<>(ProgramServiceAuthHeaders.trustedInternalHeaders());
            return restTemplate.exchange(
                            "http://program-service/api/v1/invoices/{invoiceId}",
                            HttpMethod.GET,
                            entity,
                            Map.class,
                            invoiceId)
                    .getBody();
        } catch (Exception e) {
            log.warn("Failed to fetch invoice {}: {}", invoiceId, e.getMessage());
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private String fetchAnchorName(UUID anchorId) {
        try {
            HttpEntity<Void> entity = new HttpEntity<>(ProgramServiceAuthHeaders.trustedInternalHeaders());
            Map<String, Object> response = restTemplate.exchange(
                            "http://program-service/api/v1/anchors/{anchorId}",
                            HttpMethod.GET,
                            entity,
                            Map.class,
                            anchorId)
                    .getBody();
            if (response == null || !"SUCCESS".equals(response.get("status"))) {
                return "";
            }
            Object data = response.get("data");
            if (data instanceof Map<?, ?> anchor) {
                String name = stringField(anchor.get("name"));
                if (!name.isBlank()) {
                    return name;
                }
                return stringField(anchor.get("anchorCode"));
            }
            return "";
        } catch (Exception e) {
            log.warn("Failed to fetch anchor {}: {}", anchorId, e.getMessage());
            return "";
        }
    }

    private static String formatMoney(BigDecimal amount, String currency) {
        if (amount == null) {
            return "";
        }
        String symbol = "INR".equalsIgnoreCase(currency) ? "₹ " : currency + " ";
        return symbol + amount.stripTrailingZeros().toPlainString();
    }

    private static BigDecimal firstNonNullAmount(BigDecimal... values) {
        for (BigDecimal value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static BigDecimal parseBigDecimal(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(raw));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate parseLocalDate(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return LocalDate.parse(String.valueOf(raw));
        } catch (Exception e) {
            return null;
        }
    }

    private static UUID parseUuid(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(raw));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String stringField(Object raw) {
        return raw != null ? String.valueOf(raw).trim() : "";
    }

    public record InvoiceContext(
            UUID invoiceId,
            String invoiceNumber,
            String vendorName,
            String dueDateLabel,
            String amountDueLabel) {}
}
