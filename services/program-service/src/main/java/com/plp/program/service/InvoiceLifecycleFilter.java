package com.plp.program.service;

import com.plp.program.model.enums.InvoiceStatus;

import java.util.Set;

public final class InvoiceLifecycleFilter {

    private static final Set<String> TERMINAL = Set.of(
            InvoiceStatus.REJECTED.name(),
            InvoiceStatus.CLOSED.name(),
            InvoiceStatus.EXPIRED.name());

    private InvoiceLifecycleFilter() {}

    public static boolean matchesLifecycle(String invoiceStatus, String lifecycle) {
        if (lifecycle == null || lifecycle.isBlank()) {
            return true;
        }
        String st = invoiceStatus == null ? "" : invoiceStatus.trim().toUpperCase();
        return switch (lifecycle.trim().toLowerCase()) {
            case "active" -> !TERMINAL.contains(st);
            case "closed" -> InvoiceStatus.REJECTED.name().equals(st) || InvoiceStatus.CLOSED.name().equals(st);
            default -> true;
        };
    }
}
