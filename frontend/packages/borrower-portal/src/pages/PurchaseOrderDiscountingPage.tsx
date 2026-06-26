import InvoiceDiscountingPage from './InvoiceDiscountingPage';
import { FLOW_PURCHASE_ORDER_DISCOUNTING } from '@plp/shared';

export default function PurchaseOrderDiscountingPage() {
  return (
    <InvoiceDiscountingPage
      flowType={FLOW_PURCHASE_ORDER_DISCOUNTING}
      title="Purchase Order Discounting"
      description="Create purchase orders for anchor approval, then request financing once eligible."
      createPath="/purchase-order-discounting/create"
      createLabel="Create purchase order"
    />
  );
}
