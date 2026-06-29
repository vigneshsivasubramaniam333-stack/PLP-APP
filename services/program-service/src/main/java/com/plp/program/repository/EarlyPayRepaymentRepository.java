package com.plp.program.repository;

import com.plp.program.model.entity.EarlyPayRepayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EarlyPayRepaymentRepository extends JpaRepository<EarlyPayRepayment, UUID> {

    List<EarlyPayRepayment> findByAnchorIdOrderByRepaidAtDesc(UUID anchorId);

    List<EarlyPayRepayment> findByBorrowerIdOrderByRepaidAtDesc(UUID borrowerId);

    List<EarlyPayRepayment> findByInvoiceId(UUID invoiceId);

    boolean existsByInvoiceId(UUID invoiceId);

    void deleteByBorrowerId(UUID borrowerId);
}
