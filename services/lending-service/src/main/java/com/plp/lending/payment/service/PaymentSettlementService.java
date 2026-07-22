package com.plp.lending.payment.service;

import com.plp.lending.payment.dto.PipSettlementRowView;
import com.plp.lending.payment.model.PaymentInProgress;
import com.plp.lending.payment.model.PaymentTransaction;
import com.plp.lending.payment.model.PgSettlementBatch;
import com.plp.lending.payment.repository.PaymentInProgressRepository;
import com.plp.lending.payment.repository.PaymentTransactionRepository;
import com.plp.lending.payment.repository.PgSettlementBatchRepository;
import com.plp.lending.service.LoanService;
import com.plp.lending.integration.BorrowerContactClient;
import com.plp.lending.integration.ProgramServiceAuthHeaders;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentSettlementService {

    private final PaymentInProgressRepository pipRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final PgSettlementBatchRepository batchRepository;
    private final LoanService loanService;
    private final RestTemplate restTemplate;
    private final BorrowerContactClient borrowerContactClient;

    @Transactional(readOnly = true)
    public List<PaymentInProgress> listOpenPip() {
        return pipRepository.findByPipStatusOrderByCreatedAtDesc(PaymentCheckoutService.PIP_OPEN);
    }

    @Transactional(readOnly = true)
    public List<PipSettlementRowView> listOpenPipViews() {
        return enrich(listOpenPip());
    }

    @Transactional(readOnly = true)
    public List<PipSettlementRowView> listSettledPipViews() {
        return enrich(pipRepository.findByPipStatusOrderByCreatedAtDesc("SETTLED"));
    }

    @Transactional(readOnly = true)
    public List<PaymentTransaction> listSuccessfulTransactions() {
        return transactionRepository.findByStatusOrderByCreatedAtDesc("SUCCESS");
    }

    private List<PipSettlementRowView> enrich(List<PaymentInProgress> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Set<UUID> invoiceIds = new HashSet<>();
        Set<UUID> borrowerIds = new HashSet<>();
        for (PaymentInProgress row : rows) {
            if (row.getInvoiceId() != null) {
                invoiceIds.add(row.getInvoiceId());
            }
            if (row.getBorrowerId() != null) {
                borrowerIds.add(row.getBorrowerId());
            }
        }
        Map<UUID, String> invoiceNumbers = new HashMap<>();
        for (UUID invoiceId : invoiceIds) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> response = restTemplate.exchange(
                                "http://program-service/api/v1/invoices/{id}",
                                HttpMethod.GET,
                                new HttpEntity<>(ProgramServiceAuthHeaders.trustedInternalHeaders()),
                                Map.class,
                                invoiceId)
                        .getBody();
                Object data = response != null ? response.get("data") : null;
                if (data instanceof Map<?, ?> m) {
                    Object num = m.get("invoiceNumber");
                    if (num != null && !num.toString().isBlank()) {
                        invoiceNumbers.put(invoiceId, num.toString().trim());
                    }
                } else if (response != null && response.get("invoiceNumber") != null) {
                    invoiceNumbers.put(invoiceId, response.get("invoiceNumber").toString().trim());
                }
            } catch (Exception e) {
                log.warn("Could not resolve invoiceNumber for {}: {}", invoiceId, e.getMessage());
            }
        }
        Map<UUID, String> borrowerNames = new HashMap<>();
        for (UUID borrowerId : borrowerIds) {
            borrowerNames.put(borrowerId, borrowerContactClient.fetch(borrowerId).name());
        }
        List<PipSettlementRowView> out = new ArrayList<>(rows.size());
        for (PaymentInProgress row : rows) {
            out.add(new PipSettlementRowView(
                    row.getId(),
                    row.getPgTransactionId(),
                    row.getInvoiceId(),
                    invoiceNumbers.get(row.getInvoiceId()),
                    row.getLoanId(),
                    row.getBorrowerId(),
                    borrowerNames.get(row.getBorrowerId()),
                    row.getPrincipalAmount(),
                    row.getDiscountAmount(),
                    row.getPipStatus(),
                    row.getSettlementBatchId(),
                    row.getSettledAt(),
                    row.getCreatedAt()));
        }
        return out;
    }

    @Transactional
    public PgSettlementBatch createAndApplyBatch(
            LocalDate settlementDate,
            String settlementUtr,
            List<UUID> pipIds,
            String createdBy,
            String remarks) {
        PgSettlementBatch batch = PgSettlementBatch.builder()
                .settlementDate(settlementDate)
                .settlementUtr(settlementUtr)
                .settlementMode("PG")
                .status("PENDING")
                .createdBy(createdBy)
                .remarks(remarks)
                .build();
        batch = batchRepository.save(batch);
        for (UUID pipId : pipIds) {
            PaymentInProgress pip = pipRepository.findById(pipId)
                    .orElseThrow(() -> new IllegalArgumentException("PIP not found: " + pipId));
            if (!PaymentCheckoutService.PIP_OPEN.equals(pip.getPipStatus())) {
                throw new IllegalArgumentException("PIP already settled: " + pipId);
            }
            loanService.recordSettlementRepayment(
                    pip.getLoanId(),
                    pip.getPrincipalAmount(),
                    "PAYU_PG_SETTLED",
                    settlementUtr);
            pip.setPipStatus("SETTLED");
            pip.setSettlementBatchId(batch.getId());
            pip.setSettledAt(Instant.now());
            pipRepository.save(pip);
            adjustInvoicePipSettled(pip.getInvoiceId(), pip.getPrincipalAmount(), pip.getDiscountAmount());
        }
        batch.setStatus("APPLIED");
        batch.setAppliedAt(Instant.now());
        return batchRepository.save(batch);
    }

    private void adjustInvoicePipSettled(UUID invoiceId, BigDecimal principal, BigDecimal discount) {
        Map<String, Object> body = Map.of(
                "principalSubtract", principal,
                "discountSubtract", discount != null ? discount : BigDecimal.ZERO);
        restTemplate.exchange(
                "http://program-service/api/v1/invoices/{id}/pip-adjust",
                HttpMethod.POST,
                new HttpEntity<>(body, ProgramServiceAuthHeaders.trustedInternalJsonHeaders()),
                Map.class,
                invoiceId);
    }
}
