package com.plp.lending.payment.repository;

import com.plp.lending.payment.model.PgSettlementBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PgSettlementBatchRepository extends JpaRepository<PgSettlementBatch, UUID> {}
