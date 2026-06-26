package com.plp.program.model.enums;

/**
 * Invoice discounting sub-program flow types.
 * <ul>
 *   <li>{@link #PURCHASE_BILL_DISCOUNTING} — anchor creates (PBF)</li>
 *   <li>{@link #SALES_BILL_DISCOUNTING} — borrower creates sales bill (SBD)</li>
 *   <li>{@link #PURCHASE_ORDER_DISCOUNTING} — borrower creates purchase order (PO)</li>
 * </ul>
 */
public final class InvoiceDiscountingFlowType {

    public static final String PURCHASE_BILL_DISCOUNTING = "PURCHASE_BILL_DISCOUNTING";
    public static final String SALES_BILL_DISCOUNTING = "SALES_BILL_DISCOUNTING";
    public static final String PURCHASE_ORDER_DISCOUNTING = "PURCHASE_ORDER_DISCOUNTING";

    private InvoiceDiscountingFlowType() {
    }

    public static boolean isPurchaseBill(String flowType) {
        return flowType == null || flowType.isBlank() || PURCHASE_BILL_DISCOUNTING.equalsIgnoreCase(flowType.trim());
    }

    public static boolean isSalesBill(String flowType) {
        return flowType != null && SALES_BILL_DISCOUNTING.equalsIgnoreCase(flowType.trim());
    }

    public static boolean isPurchaseOrder(String flowType) {
        return flowType != null && PURCHASE_ORDER_DISCOUNTING.equalsIgnoreCase(flowType.trim());
    }

    /** SBD or PO — borrower-initiated; anchor approves before finance. */
    public static boolean isSellerInitiated(String flowType) {
        return isSalesBill(flowType) || isPurchaseOrder(flowType);
    }

    public static boolean isKnown(String flowType) {
        if (flowType == null || flowType.isBlank()) {
            return true;
        }
        String n = flowType.trim().toUpperCase();
        return PURCHASE_BILL_DISCOUNTING.equals(n)
                || SALES_BILL_DISCOUNTING.equals(n)
                || PURCHASE_ORDER_DISCOUNTING.equals(n);
    }

    public static void requireKnown(String flowType) {
        if (!isKnown(flowType)) {
            throw new IllegalArgumentException(
                    "Invalid flowType: " + flowType + ". Use PURCHASE_BILL_DISCOUNTING, SALES_BILL_DISCOUNTING, or "
                            + PURCHASE_ORDER_DISCOUNTING);
        }
    }
}
