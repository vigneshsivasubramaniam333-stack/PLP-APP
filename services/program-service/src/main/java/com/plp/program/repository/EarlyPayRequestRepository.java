package com.plp.program.repository;

import com.plp.program.model.entity.EarlyPayRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EarlyPayRequestRepository extends JpaRepository<EarlyPayRequest, UUID> {

    Optional<EarlyPayRequest> findFirstByInvoiceIdAndStatusNot(UUID invoiceId, String status);

    Optional<EarlyPayRequest> findFirstByInvoiceIdAndStatus(UUID invoiceId, String status);

    List<EarlyPayRequest> findByInvoiceIdInAndStatus(List<UUID> invoiceIds, String status);

    List<EarlyPayRequest> findBySubProgramIdAndStatusOrderByCreatedAtDesc(UUID subProgramId, String status);

    List<EarlyPayRequest> findBySubProgramIdInAndStatusOrderByCreatedAtDesc(List<UUID> subProgramIds, String status);

    List<EarlyPayRequest> findByStatus(String status);

    void deleteByBorrowerId(UUID borrowerId);
}
