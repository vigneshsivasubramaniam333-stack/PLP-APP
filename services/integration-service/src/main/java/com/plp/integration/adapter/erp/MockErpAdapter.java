package com.plp.integration.adapter.erp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Mock ERP adapter for development/testing.
 * Simulates invoice and purchase order data.
 */
@Slf4j
@Component
public class MockErpAdapter implements ErpSystemAdapter {

    @Override
    public InvoiceInfo fetchInvoice(String invoiceNumber, UUID anchorId) {
        log.info("Mock ERP: Fetching invoice {} for anchor {}", invoiceNumber, anchorId);
        return new InvoiceInfo(
                invoiceNumber,
                "BUYER-001",
                "SELLER-001",
                BigDecimal.valueOf(500000),
                BigDecimal.valueOf(90000),
                BigDecimal.valueOf(590000),
                LocalDate.now().minusDays(5),
                LocalDate.now().plusDays(55),
                "PO-2026-001",
                "GRN-2026-001",
                "ACCEPTED",
                60
        );
    }

    @Override
    public List<InvoiceInfo> listPendingInvoices(String buyerId, UUID anchorId) {
        log.info("Mock ERP: Listing pending invoices for buyer {} under anchor {}", buyerId, anchorId);
        return List.of(
                new InvoiceInfo("INV-2026-001", buyerId, "SELLER-001", BigDecimal.valueOf(500000),
                        BigDecimal.valueOf(90000), BigDecimal.valueOf(590000),
                        LocalDate.now().minusDays(5), LocalDate.now().plusDays(55),
                        "PO-2026-001", "GRN-2026-001", "ACCEPTED", 60),
                new InvoiceInfo("INV-2026-002", buyerId, "SELLER-001", BigDecimal.valueOf(300000),
                        BigDecimal.valueOf(54000), BigDecimal.valueOf(354000),
                        LocalDate.now().minusDays(3), LocalDate.now().plusDays(57),
                        "PO-2026-002", "GRN-2026-002", "ACCEPTED", 60)
        );
    }

    @Override
    public ThreeWayMatchResult verifyThreeWayMatch(String invoiceNumber, UUID anchorId) {
        log.info("Mock ERP: 3-way match verification for invoice {} anchor {}", invoiceNumber, anchorId);
        return new ThreeWayMatchResult(
                invoiceNumber,
                true,
                true,
                true,
                true,
                null
        );
    }

    @Override
    public boolean confirmInvoiceAcceptance(String invoiceNumber, UUID anchorId) {
        log.info("Mock ERP: Invoice acceptance confirmed for {} by anchor {}", invoiceNumber, anchorId);
        return true;
    }
}
