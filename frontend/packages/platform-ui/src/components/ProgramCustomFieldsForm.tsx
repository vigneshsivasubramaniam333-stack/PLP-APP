import { useEffect, useState } from 'react';
import { programApi, type ProgramFieldDefinition } from '@plp/shared';

const FIELD_KEYS_IN_EXTRA = new Set([
  'minInvoiceAmount',
  'minDaysToDueDate',
  'dependencyVintagePercent',
]);

/** Load active program field definitions for a product type. */
export function useProgramFieldDefinitions(productType: string | undefined) {
  const [definitions, setDefinitions] = useState<ProgramFieldDefinition[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!productType) {
      setDefinitions([]);
      return;
    }
    let cancelled = false;
    setLoading(true);
    programApi
      .listFieldDefinitions({ productType, activeOnly: true })
      .then((res) => {
        if (!cancelled) setDefinitions(res.data?.data ?? []);
      })
      .catch(() => {
        if (!cancelled) setDefinitions([]);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [productType]);

  return { definitions, loading };
}

export function formValuesFromConfig(
  definitions: ProgramFieldDefinition[],
  config: Record<string, unknown> | null | undefined,
  maxTenureDays?: number | null,
): Record<string, string> {
  const out: Record<string, string> = {};
  const cfg = config ?? {};
  for (const d of definitions) {
    let v = cfg[d.fieldKey];
    if ((v == null || v === '') && d.fieldKey === 'maxTenureDays' && maxTenureDays != null) {
      v = maxTenureDays;
    }
    out[d.fieldKey] = v != null && v !== '' ? String(v) : '';
  }
  return out;
}

/** Parse definition form values into config keys (omits blanks). Dual-writes maxTenureDays to caller via return. */
export function configFromCustomFieldForm(
  definitions: ProgramFieldDefinition[],
  values: Record<string, string>,
): { config: Record<string, number | string>; maxTenureDays?: number } {
  const config: Record<string, number | string> = {};
  let maxTenureDays: number | undefined;
  for (const d of definitions) {
    const raw = (values[d.fieldKey] ?? '').trim();
    if (!raw) {
      if (d.required) {
        throw new Error(`${d.label} is required`);
      }
      continue;
    }
    if (d.inputType === 'NUMBER') {
      const n = Number(raw);
      if (Number.isNaN(n)) throw new Error(`${d.label} must be a number`);
      if (d.fieldKey === 'maxTenureDays') {
        maxTenureDays = Math.trunc(n);
        continue;
      }
      config[d.fieldKey] = n;
    } else if (d.inputType === 'DROPDOWN') {
      config[d.fieldKey] = raw.toUpperCase();
    } else {
      config[d.fieldKey] = raw;
    }
  }
  return { config, maxTenureDays };
}

export function ProgramCustomFieldsForm({
  definitions,
  values,
  onChange,
  disabled,
  loading,
  excludeKeys,
}: {
  definitions: ProgramFieldDefinition[];
  values: Record<string, string>;
  onChange: (next: Record<string, string>) => void;
  disabled?: boolean;
  loading?: boolean;
  excludeKeys?: Set<string>;
}) {
  const defs = definitions.filter((d) => !excludeKeys?.has(d.fieldKey) && !FIELD_KEYS_IN_EXTRA.has(d.fieldKey));

  if (loading) {
    return <p className="text-sm text-slate-500 sm:col-span-2">Loading program fields…</p>;
  }
  if (!defs.length) return null;

  const setKey = (key: string, value: string) => onChange({ ...values, [key]: value });

  const inputCls =
    'w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 shadow-sm focus:border-slate-400 focus:outline-none focus:ring-2 focus:ring-slate-200';

  return (
    <div className="sm:col-span-2 space-y-3">
      <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">
        Program custom / eligibility fields
      </p>
      <div className="grid gap-3 sm:grid-cols-2">
        {defs.map((d) => {
          const val = values[d.fieldKey] ?? '';
          const label = (
            <>
              {d.label}
              {d.required ? <span className="text-rose-600"> *</span> : null}
            </>
          );
          if (d.inputType === 'DROPDOWN') {
            return (
              <label key={d.id} className="block text-sm font-medium text-slate-700">
                {label}
                <select
                  className={`${inputCls} mt-1`}
                  value={val}
                  onChange={(e) => setKey(d.fieldKey, e.target.value)}
                  disabled={disabled}
                >
                  <option value="">Select</option>
                  {(d.options ?? []).map((o) => {
                    const v = String(o.value ?? '');
                    const lab = String(o.label ?? v);
                    return (
                      <option key={v} value={v}>
                        {lab}
                      </option>
                    );
                  })}
                </select>
                {d.helpText ? (
                  <span className="mt-0.5 block text-xs font-normal text-slate-500">{d.helpText}</span>
                ) : null}
              </label>
            );
          }
          if (d.inputType === 'NUMBER') {
            return (
              <label key={d.id} className="block text-sm font-medium text-slate-700">
                {label}
                <input
                  type="number"
                  className={`${inputCls} mt-1`}
                  value={val}
                  onChange={(e) => setKey(d.fieldKey, e.target.value)}
                  disabled={disabled}
                />
                {d.helpText ? (
                  <span className="mt-0.5 block text-xs font-normal text-slate-500">{d.helpText}</span>
                ) : null}
              </label>
            );
          }
          return (
            <label key={d.id} className="block text-sm font-medium text-slate-700">
              {label}
              <input
                type="text"
                className={`${inputCls} mt-1`}
                value={val}
                onChange={(e) => setKey(d.fieldKey, e.target.value)}
                disabled={disabled}
              />
              {d.helpText ? (
                <span className="mt-0.5 block text-xs font-normal text-slate-500">{d.helpText}</span>
              ) : null}
            </label>
          );
        })}
      </div>
    </div>
  );
}
