package com.plp.program.model.dto;

import java.util.List;

public record InvoiceCsvUploadResult(
        List<com.plp.program.model.entity.Invoice> invoices,
        int rowsProcessed,
        int rowsSkipped,
        List<CsvRowError> errors) {

    public record CsvRowError(int row, String reason) {}
}
