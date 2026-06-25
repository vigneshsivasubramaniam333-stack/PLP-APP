import { useCallback, useEffect, useState } from 'react';
import {
  repaymentDefaultsApi,
  notifyError,
  notifySuccess,
  type ProductRepaymentDefault,
  type ProductType,
} from '@plp/shared';

const PRODUCT_LABELS: Record<ProductType, string> = {
  PAY_DAY_LOAN: 'Pay Day Loan',
  INVOICE_DISCOUNTING: 'Invoice Discounting',
};

const MECHANISM_LABELS: Record<string, string> = {
  SMART_COLLECT: 'Smart Collect',
  PAYU_PG: 'PayU (Payment Gateway)',
  API_PG: 'API-based PG (reserved)',
};

type EditState = {
  repaymentMechanism: string;
  pgProviderCode: string;
  enabled: boolean;
};

function toEdit(row: ProductRepaymentDefault): EditState {
  return {
    repaymentMechanism: row.repaymentMechanism ?? 'SMART_COLLECT',
    pgProviderCode: row.pgProviderCode ?? '',
    enabled: row.enabled !== false,
  };
}

export default function RepaymentDefaultsPage() {
  const [rows, setRows] = useState<ProductRepaymentDefault[]>([]);
  const [edits, setEdits] = useState<Record<string, EditState>>({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await repaymentDefaultsApi.list();
      const data = res.data?.data ?? [];
      setRows(data);
      const next: Record<string, EditState> = {};
      for (const row of data) {
        next[row.productType] = toEdit(row);
      }
      setEdits(next);
    } catch (err) {
      notifyError(err, 'Could not load repayment defaults');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const save = async (productType: ProductType) => {
    const edit = edits[productType];
    if (!edit) return;
    setSaving(productType);
    try {
      await repaymentDefaultsApi.update(productType, {
        repaymentMechanism: edit.repaymentMechanism,
        pgProviderCode: edit.pgProviderCode.trim() || null,
        enabled: edit.enabled,
      });
      notifySuccess(`${PRODUCT_LABELS[productType]} default saved`);
      await load();
    } catch (err) {
      notifyError(err, 'Save failed');
    } finally {
      setSaving(null);
    }
  };

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-slate-800">Repayment defaults</h1>
        <p className="text-sm text-slate-500 mt-1 max-w-3xl">
          Platform-wide default repayment mechanism per product type. Borrowers enrolled with{' '}
          <strong className="font-medium text-slate-700">Use platform default</strong> on a sub-program inherit
          these settings. Per-borrower custom payment method on sub-program enrollment overrides this default.
        </p>
      </div>

      {loading ? (
        <p className="text-sm text-slate-500">Loading…</p>
      ) : (
        <div className="space-y-4">
          {rows.map((row) => {
            const edit = edits[row.productType] ?? toEdit(row);
            const isPg = edit.repaymentMechanism === 'PAYU_PG' || edit.repaymentMechanism === 'API_PG';
            return (
              <div
                key={row.productType}
                className="bg-white rounded-xl border border-slate-200 shadow-sm p-5"
              >
                <div className="flex flex-wrap items-start justify-between gap-3 mb-4">
                  <div>
                    <h2 className="text-lg font-semibold text-slate-800">
                      {PRODUCT_LABELS[row.productType]}
                    </h2>
                    <p className="text-xs text-slate-500 font-mono mt-0.5">{row.productType}</p>
                  </div>
                  {row.updatedAt ? (
                    <p className="text-xs text-slate-400">
                      Updated {new Date(row.updatedAt).toLocaleString()}
                      {row.updatedBy ? ` · ${row.updatedBy}` : ''}
                    </p>
                  ) : null}
                </div>
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                  <div>
                    <label className="block text-xs font-semibold text-slate-500 uppercase mb-1">
                      Repayment mechanism
                    </label>
                    <select
                      value={edit.repaymentMechanism}
                      onChange={(e) =>
                        setEdits((prev) => ({
                          ...prev,
                          [row.productType]: { ...edit, repaymentMechanism: e.target.value },
                        }))
                      }
                      className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
                    >
                      {Object.entries(MECHANISM_LABELS).map(([value, label]) => (
                        <option key={value} value={value}>
                          {label}
                        </option>
                      ))}
                    </select>
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-slate-500 uppercase mb-1">
                      PG provider code
                    </label>
                    <input
                      type="text"
                      value={edit.pgProviderCode}
                      disabled={!isPg}
                      placeholder={isPg ? 'e.g. PAYU' : 'N/A'}
                      onChange={(e) =>
                        setEdits((prev) => ({
                          ...prev,
                          [row.productType]: { ...edit, pgProviderCode: e.target.value },
                        }))
                      }
                      className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm disabled:bg-slate-50"
                    />
                  </div>
                  <div className="flex items-end gap-4">
                    <label className="flex items-center gap-2 text-sm text-slate-700 pb-2">
                      <input
                        type="checkbox"
                        checked={edit.enabled}
                        onChange={(e) =>
                          setEdits((prev) => ({
                            ...prev,
                            [row.productType]: { ...edit, enabled: e.target.checked },
                          }))
                        }
                      />
                      Enabled
                    </label>
                    <button
                      type="button"
                      disabled={saving === row.productType}
                      onClick={() => void save(row.productType)}
                      className="ml-auto bg-indigo-600 text-white px-4 py-2 rounded-lg text-sm font-semibold hover:bg-indigo-700 disabled:opacity-50"
                    >
                      {saving === row.productType ? 'Saving…' : 'Save'}
                    </button>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
