package com.plp.lending.payment.service;

import com.plp.lending.payment.model.PaymentInProgress;
import com.plp.lending.payment.model.PaymentTransaction;
import com.plp.lending.payment.model.PgSettlementBatch;
import com.plp.lending.payment.repository.PaymentInProgressRepository;
import com.plp.lending.payment.repository.PaymentTransactionRepository;
import com.plp.lending.payment.repository.PgSettlementBatchRepository;
import com.plp.lending.service.LoanService;
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
import java.util.List;
import java.util.Map;
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

    @Transactional(readOnly = true)
    public List<PaymentInProgress> listOpenPip() {
        return pipRepository.findByPipStatusOrderByCreatedAtDesc(PaymentCheckoutService.PIP_OPEN);
    }

    @Transactional(readOnly = true)
    public List<PaymentTransaction> listSuccessfulTransactions() {
        return transactionRepository.findByStatusOrderByCreatedAtDesc("SUCCESS");
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
