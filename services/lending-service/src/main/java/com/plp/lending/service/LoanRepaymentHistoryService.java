package com.plp.lending.service;

import com.plp.lending.audit.AuditEvent;
import com.plp.lending.audit.AuditEventRepository;
import com.plp.lending.model.entity.LmsLoanOperation;
import com.plp.lending.model.entity.Repayment;
import com.plp.lending.repository.LmsLoanOperationRepository;
import com.plp.lending.repository.RepaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LoanRepaymentHistoryService {

    private final RepaymentRepository repaymentRepository;
    private final LmsLoanOperationRepository lmsLoanOperationRepository;
    private final AuditEventRepository auditEventRepository;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listForLoan(UUID loanId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Repayment r : repaymentRepository.findByLoanId(loanId)) {
            if (r.getPaidAmount() == null || r.getPaidAmount().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            rows.add(toRow(
                    r.getId(),
                    r.getPaidDate() != null ? r.getPaidDate().atStartOfDay().toInstant(java.time.ZoneOffset.UTC) : r.getCreatedAt(),
                    r.getPaidAmount(),
                    r.getRepaymentRef(),
                    "RECORDED",
                    r.getStatus() != null ? r.getStatus().name() : "SUCCESS",
                    r.getPaymentMode()));
        }

        if (rows.isEmpty()) {
            for (LmsLoanOperation op : lmsLoanOperationRepository.findByLoanIdOrderByCreatedAtDesc(loanId)) {
                if (!"REPAY".equals(op.getOperation()) || !"SUCCESS".equals(op.getStatus())) {
                    continue;
                }
                BigDecimal amount = parseLmsRepayAmount(op.getRequestJson());
                if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                rows.add(toRow(
                        op.getId(),
                        op.getCreatedAt(),
                        amount,
                        op.getResponseJson(),
                        "LMS",
                        "SUCCESS",
                        "Encore"));
            }
        }

        if (rows.isEmpty()) {
            for (AuditEvent event : auditEventRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc("LOAN", loanId.toString())) {
                if (!isRepaymentAudit(event.getAction())) {
                    continue;
                }
                BigDecimal amount = parseAuditAmount(event.getMessage());
                if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                rows.add(toRow(
                        event.getId(),
                        event.getCreatedAt(),
                        amount,
                        event.getAction(),
                        "AUDIT",
                        event.getStatus(),
                        null));
            }
        }

        rows.sort(Comparator.comparing(m -> (Instant) m.get("paidAt"), Comparator.nullsLast(Comparator.reverseOrder())));
        return rows;
    }

    private static boolean isRepaymentAudit(String action) {
        return action != null && (action.equals("REPAYMENT") || action.equals("REPAYMENT_RECORDED"));
    }

    private static BigDecimal parseLmsRepayAmount(String requestJson) {
        if (requestJson == null || requestJson.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(requestJson.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal parseAuditAmount(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(message.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Map<String, Object> toRow(
            UUID id,
            Instant paidAt,
            BigDecimal amount,
            String reference,
            String source,
            String status,
            String paymentMode) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("paidAt", paidAt);
        row.put("amount", amount);
        row.put("reference", reference);
        row.put("source", source);
        row.put("status", status);
        row.put("paymentMode", paymentMode);
        return row;
    }
}
