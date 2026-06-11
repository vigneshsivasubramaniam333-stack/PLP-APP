import { useCallback, useEffect, useState } from 'react';
import { borrowerApi, portalApi, subProgramApi } from '../api/client';
import type { CreditLimitRow } from '../components/CreditLimitDisplay';
import type { BorrowerLimit, Program, SubProgram } from '../types';

function n(v: unknown): number {
  const x = Number(v);
  return Number.isFinite(x) ? x : 0;
}

/** Load borrower credit limits (sub-program memberships, then program-level fallback). */
export function useBorrowerCreditLimits(borrowerId: string) {
  const [rows, setRows] = useState<CreditLimitRow[]>([]);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    if (!borrowerId) {
      setRows([]);
      return;
    }
    setLoading(true);
    try {
      const spRes = await subProgramApi.list();
      const subPrograms = (spRes.data?.data as SubProgram[] | undefined) ?? [];
      const limitRows: CreditLimitRow[] = [];

      for (const sp of subPrograms) {
        try {
          const summaryRes = await subProgramApi.getBorrowerLimitSummary(sp.id, borrowerId);
          const data = summaryRes.data?.data as Record<string, unknown> | undefined;
          if (!data) continue;
          const limit = n(data.borrowerLimit);
          const utilized = n(data.utilizedLimit);
          const available = n(data.availableLimit);
          if (limit > 0 || utilized > 0) {
            limitRows.push({
              label: `${sp.code} — ${sp.name}`,
              limit,
              utilized,
              available: available > 0 ? available : Math.max(0, limit - utilized),
            });
          }
        } catch {
          /* skip sub-programs without membership */
        }
      }

      if (limitRows.length > 0) {
        setRows(limitRows);
        return;
      }

      const limitsRes = await borrowerApi.getLimits(borrowerId);
      const bl = limitsRes.data?.data as BorrowerLimit | undefined;
      if (bl) {
        setRows([
          {
            label: 'Program limit',
            limit: n(bl.sanctionedLimit),
            utilized: n(bl.utilizedLimit),
            available: n(bl.availableLimit),
          },
        ]);
      } else {
        setRows([]);
      }
    } catch {
      setRows([]);
    } finally {
      setLoading(false);
    }
  }, [borrowerId]);

  useEffect(() => {
    void load();
  }, [load]);

  return { rows, loading, reload: load };
}

/** Load anchor program credit limits for dashboard / sidebar. */
export function useAnchorCreditLimits(anchorId: string) {
  const [rows, setRows] = useState<CreditLimitRow[]>([]);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    if (!anchorId) {
      setRows([]);
      return;
    }
    setLoading(true);
    try {
      const [progRes, spRes] = await Promise.all([
        portalApi.anchorPrograms(anchorId),
        subProgramApi.list().catch(() => ({ data: { data: [] } })),
      ]);

      const programs = (progRes.data?.data as Program[] | undefined) ?? [];
      const subPrograms = (spRes.data?.data as SubProgram[] | undefined) ?? [];

      const limitRows: CreditLimitRow[] = [];

      for (const p of programs) {
        const limit = n(p.programLimit);
        const utilized = n(p.utilizedLimit);
        const available = n(p.availableLimit) || Math.max(0, limit - utilized);
        if (limit > 0) {
          limitRows.push({
            label: p.programName || p.programCode,
            limit,
            utilized,
            available,
          });
        }
      }

      for (const sp of subPrograms) {
        const limit = n(sp.subProgramLimit);
        if (limit <= 0) continue;
        const utilized = n(sp.utilizedLimit);
        const available = n(sp.availableLimit) || Math.max(0, limit - utilized);
        limitRows.push({
          label: `${sp.code} — ${sp.name}`,
          limit,
          utilized,
          available,
        });
      }

      setRows(limitRows);
    } catch {
      setRows([]);
    } finally {
      setLoading(false);
    }
  }, [anchorId]);

  useEffect(() => {
    void load();
  }, [load]);

  return { rows, loading, reload: load };
}
