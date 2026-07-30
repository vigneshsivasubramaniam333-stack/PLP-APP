import { useEffect, useState } from 'react';
import { portalApi, subProgramApi } from '@plp/shared';
import type { Program, SubProgram } from '@plp/shared';
import {
  FLOW_PURCHASE_BILL_DISCOUNTING,
  FLOW_SALES_BILL_DISCOUNTING,
  FLOW_PURCHASE_ORDER_DISCOUNTING,
} from '@plp/shared';

export type AnchorFlowEnrollmentFlags = {
  paydayLoan: boolean;
  invoiceDiscounting: boolean;
  purchaseBill: boolean;
  salesBill: boolean;
  purchaseOrder: boolean;
  loading: boolean;
};

export function useAnchorFlowSubPrograms(anchorId: string): AnchorFlowEnrollmentFlags {
  const [flags, setFlags] = useState<AnchorFlowEnrollmentFlags>({
    paydayLoan: false,
    invoiceDiscounting: false,
    purchaseBill: false,
    salesBill: false,
    purchaseOrder: false,
    loading: true,
  });

  useEffect(() => {
    if (!anchorId) {
      setFlags({
        paydayLoan: false,
        invoiceDiscounting: false,
        purchaseBill: false,
        salesBill: false,
        purchaseOrder: false,
        loading: false,
      });
      return;
    }
    let cancelled = false;
    (async () => {
      try {
        const [progRes, spRes] = await Promise.all([
          portalApi.anchorPrograms(anchorId),
          subProgramApi.list(),
        ]);
        const programs = (progRes.data?.data as Program[] | undefined) ?? [];
        const subPrograms = (spRes.data?.data as SubProgram[] | undefined) ?? [];
        const activePrograms = programs.filter((p) => p.status === 'ACTIVE');
        const hasPaydayLoan = activePrograms.some((p) => p.productType === 'PAY_DAY_LOAN');
        const hasInvoiceDiscounting = activePrograms.some((p) => p.productType === 'INVOICE_DISCOUNTING');
        let pbf = false;
        let sbd = false;
        let po = false;
        for (const sp of subPrograms) {
          if (sp.status !== 'ACTIVE') continue;
          if (sp.anchorId && sp.anchorId !== anchorId) continue;
          const parent = programs.find((p) => p.id === sp.programId);
          if (parent?.productType !== 'INVOICE_DISCOUNTING') continue;
          const ft = (sp.flowType ?? '').trim() || FLOW_PURCHASE_BILL_DISCOUNTING;
          if (ft === FLOW_PURCHASE_BILL_DISCOUNTING) pbf = true;
          else if (ft === FLOW_SALES_BILL_DISCOUNTING) sbd = true;
          else if (ft === FLOW_PURCHASE_ORDER_DISCOUNTING) po = true;
        }
        // Show the base invoice entry as soon as the anchor has any active invoice discounting
        // program, even before a borrower/sub-program link is created.
        if (hasInvoiceDiscounting) {
          pbf = true;
        }
        if (!cancelled) {
          setFlags({
            paydayLoan: hasPaydayLoan,
            invoiceDiscounting: hasInvoiceDiscounting,
            purchaseBill: pbf,
            salesBill: sbd,
            purchaseOrder: po,
            loading: false,
          });
        }
      } catch {
        if (!cancelled) {
          setFlags({
            paydayLoan: false,
            invoiceDiscounting: false,
            purchaseBill: false,
            salesBill: false,
            purchaseOrder: false,
            loading: false,
          });
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [anchorId]);

  return flags;
}
