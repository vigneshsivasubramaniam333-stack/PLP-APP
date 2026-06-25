package com.plp.lending.payment.repository;

import com.plp.lending.payment.model.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

    Optional<PaymentTransaction> findByPgTransactionRef(String pgTransactionRef);

    List<PaymentTransaction> findByStatusOrderByCreatedAtDesc(String status);
}
