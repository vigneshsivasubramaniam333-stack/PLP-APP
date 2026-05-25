package com.plp.integration.adapter.erp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Adapter interface for ERP system integrations.
 * Each ERP provider (SAP, Tally, Zoho, etc.) implements this interface.
 */
public interface ErpSystemAdapter {

    /**
     * Fetch invoice details by invoice number.
     */
    InvoiceInfo fetchInvoice(String invoiceNumber, UUID anchorId);

    /**
     * List pending invoices for a buyer/borrower.
     */
    List<InvoiceInfo> listPendingInvoices(String buyerId, UUID anchorId);

    /**
     * Verify 3-way match: PO + GRN + Invoice.
     */
    ThreeWayMatchResult verifyThreeWayMatch(String invoiceNumber, UUID anchorId);

    /**
     * Confirm invoice acceptance by seller/anchor.
     */
    boolean confirmInvoiceAcceptance(String invoiceNumber, UUID anchorId);

    record InvoiceInfo(
            String invoiceNumber,
            String buyerId,
            String sellerId,
            BigDecimal invoiceAmount,
            BigDecimal gstAmount,
            BigDecimal netAmount,
            LocalDate invoiceDate,
            LocalDate dueDate,
            String poNumber,
            String grnNumber,
            String status,
            int creditDays
    ) {}

    record ThreeWayMatchResult(
            String invoiceNumber,
            boolean poMatched,
            boolean grnMatched,
            boolean amountMatched,
            boolean isFullyMatched,
            String mismatchReason
    ) {}
}
