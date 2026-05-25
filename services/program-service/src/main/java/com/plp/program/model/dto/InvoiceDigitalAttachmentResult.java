package com.plp.program.model.dto;

/**
 * Result of attaching a digital invoice file (optional object storage + always metadata).
 */
public record InvoiceDigitalAttachmentResult(String storageMode, String todo) {
}
