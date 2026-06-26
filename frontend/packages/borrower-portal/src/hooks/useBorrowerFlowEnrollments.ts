import { useEffect, useState } from 'react';
import { subProgramApi } from '@plp/shared';
import type { SubProgram } from '@plp/shared';
import {
  FLOW_PURCHASE_BILL_DISCOUNTING,
  FLOW_SALES_BILL_DISCOUNTING,
  FLOW_PURCHASE_ORDER_DISCOUNTING,
} from '@plp/shared';

export type BorrowerFlowEnrollmentFlags = {
  purchaseBill: boolean;
  salesBill: boolean;
  purchaseOrder: boolean;
  loading: boolean;
};

export function useBorrowerFlowEnrollments(borrowerId: string): BorrowerFlowEnrollmentFlags {
  const [flags, setFlags] = useState<BorrowerFlowEnrollmentFlags>({
    purchaseBill: false,
    salesBill: false,
    purchaseOrder: false,
    loading: true,
  });

  useEffect(() => {
    if (!borrowerId) {
      setFlags({ purchaseBill: false, salesBill: false, purchaseOrder: false, loading: false });
      return;
    }
    let cancelled = false;
    (async () => {
      try {
        const spRes = await subProgramApi.list();
        const subPrograms = (spRes.data?.data as SubProgram[] | undefined) ?? [];
        let pbf = false;
        let sbd = false;
        let po = false;
        for (const sp of subPrograms) {
          if (sp.status !== 'ACTIVE' || !sp.id) continue;
          try {
            const limitRes = await subProgramApi.getBorrowerLimitSummary(sp.id, borrowerId);
            if (!limitRes.data?.data) continue;
          } catch {
            continue;
          }
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
  }, [borrowerId]);

  return flags;
}
