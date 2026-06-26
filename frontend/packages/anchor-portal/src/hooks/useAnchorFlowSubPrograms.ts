import { useEffect, useState } from 'react';
import { programApi, subProgramApi } from '@plp/shared';
import type { Program, SubProgram } from '@plp/shared';
import {
  FLOW_PURCHASE_BILL_DISCOUNTING,
  FLOW_SALES_BILL_DISCOUNTING,
  FLOW_PURCHASE_ORDER_DISCOUNTING,
} from '@plp/shared';

export type AnchorFlowEnrollmentFlags = {
  purchaseBill: boolean;
  salesBill: boolean;
  purchaseOrder: boolean;
  loading: boolean;
};

export function useAnchorFlowSubPrograms(anchorId: string): AnchorFlowEnrollmentFlags {
  const [flags, setFlags] = useState<AnchorFlowEnrollmentFlags>({
    purchaseBill: false,
    salesBill: false,
    purchaseOrder: false,
    loading: true,
  });

  useEffect(() => {
    if (!anchorId) {
      setFlags({ purchaseBill: false, salesBill: false, purchaseOrder: false, loading: false });
      return;
    }
    let cancelled = false;
    (async () => {
      try {
        const [progRes, spRes] = await Promise.all([
          programApi.list(),
          subProgramApi.list(),
        ]);
        const programs = (progRes.data?.data as Program[] | undefined) ?? [];
        const subPrograms = (spRes.data?.data as SubProgram[] | undefined) ?? [];
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
        if (!cancelled) {
          setFlags({ purchaseBill: pbf, salesBill: sbd, purchaseOrder: po, loading: false });
        }
      } catch {
        if (!cancelled) {
          setFlags({ purchaseBill: false, salesBill: false, purchaseOrder: false, loading: false });
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [anchorId]);

  return flags;
}
