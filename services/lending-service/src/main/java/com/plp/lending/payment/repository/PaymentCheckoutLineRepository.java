package com.plp.lending.payment.repository;

import com.plp.lending.payment.model.PaymentCheckoutLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentCheckoutLineRepository extends JpaRepository<PaymentCheckoutLine, UUID> {

    List<PaymentCheckoutLine> findByBorrowerIdAndStatusOrderByCreatedAtDesc(UUID borrowerId, String status);

    long countByBorrowerIdAndStatus(UUID borrowerId, String status);

    Optional<PaymentCheckoutLine> findByBorrowerIdAndInvoiceIdAndStatus(UUID borrowerId, UUID invoiceId, String status);

    List<PaymentCheckoutLine> findByPgTransactionId(UUID pgTransactionId);
}
