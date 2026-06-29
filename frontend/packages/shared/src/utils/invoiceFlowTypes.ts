export const FLOW_PURCHASE_BILL_DISCOUNTING = 'PURCHASE_BILL_DISCOUNTING';
export const FLOW_SALES_BILL_DISCOUNTING = 'SALES_BILL_DISCOUNTING';
export const FLOW_PURCHASE_ORDER_DISCOUNTING = 'PURCHASE_ORDER_DISCOUNTING';

export type InvoiceDiscountingFlowType =
  | typeof FLOW_PURCHASE_BILL_DISCOUNTING
  | typeof FLOW_SALES_BILL_DISCOUNTING
  | typeof FLOW_PURCHASE_ORDER_DISCOUNTING;

export function isPurchaseBillFlow(flowType: string | null | undefined): boolean {
  const ft = (flowType ?? '').trim();
  return !ft || ft === FLOW_PURCHASE_BILL_DISCOUNTING;
}

export function isSalesBillFlow(flowType: string | null | undefined): boolean {
  return (flowType ?? '').trim() === FLOW_SALES_BILL_DISCOUNTING;
}

export function isPurchaseOrderFlow(flowType: string | null | undefined): boolean {
  return (flowType ?? '').trim() === FLOW_PURCHASE_ORDER_DISCOUNTING;
}

export function isSellerInitiatedFlow(flowType: string | null | undefined): boolean {
  return isSalesBillFlow(flowType) || isPurchaseOrderFlow(flowType);
}

export function flowTypeLabel(flowType: string | null | undefined): string {
  switch ((flowType ?? '').trim()) {
    case FLOW_PURCHASE_BILL_DISCOUNTING:
      return 'Purchase Bill Discounting';
    case FLOW_SALES_BILL_DISCOUNTING:
      return 'Sales Bill Discounting';
    case FLOW_PURCHASE_ORDER_DISCOUNTING:
      return 'Purchase Order Discounting';
    default:
      return flowType?.trim() || '—';
  }
}

export function canBorrowerAcceptInvoice(
  status: string | null | undefined,
  flowType: string | null | undefined,
): boolean {
  return isPurchaseBillFlow(flowType) && status === 'ELIGIBLE';
}

export function canBorrowerRequestFinance(
  status: string | null | undefined,
  flowType: string | null | undefined,
): boolean {
  if (status === 'FINANCING_REQUESTED') return false;
  if (status === 'DISCOUNTED_EP' || status === 'SANCTIONED_EP') return false;
  if (isPurchaseBillFlow(flowType)) {
    return status === 'BORROWER_ACCEPTED' || status === 'PARTIALLY_DISCOUNTED';
  }
  if (isSellerInitiatedFlow(flowType)) {
    return status === 'ELIGIBLE' || status === 'PARTIALLY_DISCOUNTED';
  }
  return status === 'BORROWER_ACCEPTED' || status === 'PARTIALLY_DISCOUNTED';
}

export function canBorrowerRequestEarlyPay(inv: {
  status?: string | null;
  flowType?: string | null;
  isEarlyPayAllowed?: string | null;
  showEarlyPay?: string | null;
}): boolean {
  return (
    isSalesBillFlow(inv.flowType) &&
    inv.status === 'ELIGIBLE' &&
    inv.isEarlyPayAllowed === 'YES' &&
    inv.showEarlyPay === 'YES'
  );
}
