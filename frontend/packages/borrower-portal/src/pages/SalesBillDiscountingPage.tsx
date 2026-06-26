import InvoiceDiscountingPage from './InvoiceDiscountingPage';
import { FLOW_SALES_BILL_DISCOUNTING } from '@plp/shared';

export default function SalesBillDiscountingPage() {
  return (
    <InvoiceDiscountingPage
      flowType={FLOW_SALES_BILL_DISCOUNTING}
      title="Sales Bill Discounting"
      description="Create sales bills for anchor approval, then request financing once eligible."
      createPath="/sales-bill-discounting/create"
      createLabel="Create invoice"
    />
  );
}
