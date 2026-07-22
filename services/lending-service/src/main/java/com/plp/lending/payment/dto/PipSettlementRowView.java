package com.plp.lending.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * PIP row enriched for admin PG settlements UI (invoice number + borrower name).
 */
public record PipSettlementRowView(
        UUID id,
        UUID pgTransactionId,
        UUID invoiceId,
        String invoiceNumber,
        UUID loanId,
        UUID borrowerId,
        String borrowerName,
        BigDecimal principalAmount,
        BigDecimal discountAmount,
        String pipStatus,
        UUID settlementBatchId,
        Instant settledAt,
        Instant createdAt
) {
}
