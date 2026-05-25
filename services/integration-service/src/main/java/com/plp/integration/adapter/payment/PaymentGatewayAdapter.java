package com.plp.integration.adapter.payment;

import java.math.BigDecimal;

/**
 * Adapter interface for payment gateway integrations.
 * Supports NEFT, RTGS, IMPS, UPI disbursement and collection.
 */
public interface PaymentGatewayAdapter {

    DisbursementResult initiateDisbursement(DisbursementRequest request);

    CollectionResult initiateCollection(CollectionRequest request);

    TransactionStatus checkTransactionStatus(String transactionRef);

    record DisbursementRequest(
            String referenceId,
            BigDecimal amount,
            String beneficiaryName,
            String beneficiaryAccount,
            String beneficiaryIfsc,
            String paymentMode,
            String remarks
    ) {}

    record DisbursementResult(
            String referenceId,
            String transactionRef,
            String utrNumber,
            String status,
            String failureReason
    ) {}

    record CollectionRequest(
            String referenceId,
            BigDecimal amount,
            String debitAccountNumber,
            String debitIfsc,
            String mandateId,
            String paymentMode,
            String remarks
    ) {}

    record CollectionResult(
            String referenceId,
            String transactionRef,
            String utrNumber,
            String status,
            String failureReason
    ) {}

    record TransactionStatus(
            String transactionRef,
            String status,
            String utrNumber,
            String completedAt
    ) {}
}
