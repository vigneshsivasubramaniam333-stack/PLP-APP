package com.plp.lending.payment.repository;

import com.plp.lending.payment.model.PaymentInProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentInProgressRepository extends JpaRepository<PaymentInProgress, UUID> {

    List<PaymentInProgress> findByPipStatusOrderByCreatedAtDesc(String pipStatus);

    List<PaymentInProgress> findByBorrowerIdAndPipStatusOrderByCreatedAtDesc(UUID borrowerId, String pipStatus);

    boolean existsByInvoiceIdAndPipStatus(UUID invoiceId, String pipStatus);

    List<PaymentInProgress> findBySettlementBatchId(UUID settlementBatchId);
}
