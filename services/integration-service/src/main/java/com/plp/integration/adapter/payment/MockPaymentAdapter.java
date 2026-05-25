package com.plp.integration.adapter.payment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Mock Payment adapter for development/testing.
 * Simulates successful payment processing.
 */
@Slf4j
@Component
public class MockPaymentAdapter implements PaymentGatewayAdapter {

    @Override
    public DisbursementResult initiateDisbursement(DisbursementRequest request) {
        log.info("Mock Payment: Disbursement initiated - ref={} amount={} to={}",
                request.referenceId(), request.amount(), request.beneficiaryAccount());
        String utr = "UTR" + System.currentTimeMillis();
        return new DisbursementResult(
                request.referenceId(),
                UUID.randomUUID().toString(),
                utr,
                "SUCCESS",
                null
        );
    }

    @Override
    public CollectionResult initiateCollection(CollectionRequest request) {
        log.info("Mock Payment: Collection initiated - ref={} amount={} from={}",
                request.referenceId(), request.amount(), request.debitAccountNumber());
        String utr = "UTR" + System.currentTimeMillis();
        return new CollectionResult(
                request.referenceId(),
                UUID.randomUUID().toString(),
                utr,
                "SUCCESS",
                null
        );
    }

    @Override
    public TransactionStatus checkTransactionStatus(String transactionRef) {
        return new TransactionStatus(
                transactionRef,
                "COMPLETED",
                "UTR" + transactionRef.hashCode(),
                Instant.now().toString()
        );
    }
}
