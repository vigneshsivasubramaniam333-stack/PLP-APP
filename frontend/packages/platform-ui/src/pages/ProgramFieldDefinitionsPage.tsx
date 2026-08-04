import { useCallback, useEffect, useState } from 'react';
import { programApi, BtPageHeader, BtButton, BtCard } from '@plp/shared';
import type { ProgramFieldDefinition } from '@plp/shared';

type FormState = {
  fieldKey: string;
  label: string;
  inputType: string;
  required: boolean;
  active: boolean;
  sortOrder: string;
  productTypes: string[];
  helpText: string;
  optionsText: string;
};

const emptyForm = (): FormState => ({
  fieldKey: '',
  label: '',
  inputType: 'NUMBER',
  required: false,
  active: true,
  sortOrder: '100',
  productTypes: ['INVOICE_DISCOUNTING'],
  helpText: '',
  optionsText: '',
});

function optionsFromText(text: string): { value: string; label: string }[] | undefined {
  const lines = text
    .split('\n')
    .map((l) => l.trim())
    .filter(Boolean);
  if (!lines.length) return undefined;
  return lines.map((line) => {
    const [value, ...rest] = line.split('|');
    const v = (value ?? '').trim();
    const lab = rest.join('|').trim() || v;
    return { value: v, label: lab };
  });
}

function optionsToText(options?: { value?: string; label?: string }[] | null): string {
  if (!options?.length) return '';
  return options
    .map((o) => {
      const v = String(o.value ?? '');
      const lab = String(o.label ?? v);
      return lab && lab !== v ? `${v}|${lab}` : v;
    })
    .join('\n');
}

export default function ProgramFieldDefinitionsPage() {
  const [rows, setRows] = useState<ProgramFieldDefinition[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState<FormState>(emptyForm);
  const [showForm, setShowForm] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const res = await programApi.listFieldDefinitions({ activeOnly: false });
      setRows(res.data?.data ?? []);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const openCreate = () => {
    setEditingId(null);
    setForm(emptyForm());
    setShowForm(true);
  };

  const openEdit = (row: ProgramFieldDefinition) => {
    setEditingId(row.id);
    setForm({
      fieldKey: row.fieldKey,
      label: row.label,
      inputType: row.inputType,
      required: row.required,
      active: row.active,
      sortOrder: String(row.sortOrder ?? 0),
      productTypes: row.productTypes?.length ? [...row.productTypes] : [],
      helpText: row.helpText ?? '',
      optionsText: optionsToText(row.options),
    });
    setShowForm(true);
  };

  const handleSave = async () => {
    if (!form.label.trim()) {
      setError('Label is required');
      return;
    }
    if (form.inputType === 'DROPDOWN' && !optionsFromText(form.optionsText)?.length) {
      setError('Dropdown fields need options (one per line: value or value|label)');
      return;
    }
    setBusy(true);
    setError('');
    try {
      const body: Record<string, unknown> = {
        label: form.label.trim(),
        inputType: form.inputType,
        required: form.required,
        active: form.active,
        sortOrder: form.sortOrder ? Number(form.sortOrder) : 0,
        productTypes: form.productTypes,
        helpText: form.helpText.trim() || undefined,
        options: form.inputType === 'DROPDOWN' ? optionsFromText(form.optionsText) : undefined,
      };
      if (!editingId && form.fieldKey.trim()) {
        body.fieldKey = form.fieldKey.trim();
      }
      if (editingId) {
        await programApi.updateFieldDefinition(editingId, body);
      } else {
        await programApi.createFieldDefinition(body);
      }
      setShowForm(false);
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Save failed');
    } finally {
      setBusy(false);
    }
  };

  const handleDelete = async (row: ProgramFieldDefinition) => {
    if (row.systemManaged) return;
    if (!window.confirm(`Delete field “${row.label}”?`)) return;
    setBusy(true);
    try {
      await programApi.deleteFieldDefinition(row.id);
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Delete failed');
    } finally {
      setBusy(false);
    }
  };

  const editing = editingId ? rows.find((r) => r.id === editingId) : null;
  const keyLocked = Boolean(editing?.systemManaged || editingId);

  return (
    <div>
      <BtPageHeader
        title="Program custom fields"
        description="Configure fields shown when creating invoice-discounting programs on PLP admin and LOS (RM program setup). System fields cannot be deleted. Use the same field key if values must sync from LOS."
        actions={
          <BtButton onClick={openCreate} disabled={busy}>
            New field
          </BtButton>
        }
      />
      {error ? (
        <p className="mb-4 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">{error}</p>
      ) : null}
      {showForm ? (
        <BtCard className="mb-6 p-4">
          <h3 className="mb-3 text-sm font-semibold text-slate-800">{editingId ? 'Edit field' : 'Create field'}</h3>
          <div className="grid gap-3 sm:grid-cols-2">
            <label className="text-sm font-medium text-slate-700">
              Field key
              <input
                className="bt-input mt-1 w-full"
                value={form.fieldKey}
                onChange={(e) => setForm((f) => ({ ...f, fieldKey: e.target.value }))}
                disabled={keyLocked}
                placeholder="auto from label if blank"
              />
            </label>
            <label className="text-sm font-medium text-slate-700">
              Label *
              <input
                className="bt-input mt-1 w-full"
                value={form.label}
                onChange={(e) => setForm((f) => ({ ...f, label: e.target.value }))}
              />
            </label>
            <label className="text-sm font-medium text-slate-700">
              Type
              <select
                className="bt-input mt-1 w-full"
                value={form.inputType}
                onChange={(e) => setForm((f) => ({ ...f, inputType: e.target.value }))}
              >
                <option value="TEXT">TEXT</option>
                <option value="NUMBER">NUMBER</option>
                <option value="DROPDOWN">DROPDOWN</option>
              </select>
            </label>
            <label className="text-sm font-medium text-slate-700">
              Sort order
              <input
                type="number"
                className="bt-input mt-1 w-full"
                value={form.sortOrder}
                onChange={(e) => setForm((f) => ({ ...f, sortOrder: e.target.value }))}
              />
            </label>
            {form.inputType === 'DROPDOWN' ? (
              <label className="text-sm font-medium text-slate-700 sm:col-span-2">
                Options (value or value|label per line)
                <textarea
                  className="bt-input mt-1 w-full font-mono text-sm"
                  rows={4}
                  value={form.optionsText}
                  onChange={(e) => setForm((f) => ({ ...f, optionsText: e.target.value }))}
                />
              </label>
            ) : null}
            <label className="text-sm font-medium text-slate-700 sm:col-span-2">
              Help text
              <input
                className="bt-input mt-1 w-full"
                value={form.helpText}
                onChange={(e) => setForm((f) => ({ ...f, helpText: e.target.value }))}
              />
            </label>
            <label className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={form.required}
                onChange={(e) => setForm((f) => ({ ...f, required: e.target.checked }))}
              />
              Required
            </label>
            <label className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={form.active}
                onChange={(e) => setForm((f) => ({ ...f, active: e.target.checked }))}
              />
              Active
            </label>
          </div>
          <div className="mt-4 flex gap-2">
            <BtButton disabled={busy} onClick={() => void handleSave()}>
              {busy ? 'Saving…' : 'Save'}
            </BtButton>
            <button type="button" className="bt-btn-secondary" onClick={() => setShowForm(false)}>
              Cancel
            </button>
          </div>
        </BtCard>
      ) : null}
      {loading ? (
        <p className="text-sm text-slate-500">Loading…</p>
      ) : rows.length === 0 ? (
        <BtCard className="p-8 text-center">
          <p className="text-sm text-slate-700 font-medium">No program custom fields yet</p>
          <p className="mt-1 text-xs text-slate-500">
            Click <strong>New field</strong> to add TEXT / NUMBER / DROPDOWN fields, or ensure migration
            V36 has been applied (system eligibility fields seed automatically).
          </p>
        </BtCard>
      ) : (
        <BtCard className="overflow-x-auto p-0">
          <table className="bt-table w-full text-sm">
            <thead>
              <tr>
                <th>Label</th>
                <th>Key</th>
                <th>Type</th>
                <th>Req</th>
                <th>Active</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.id}>
                  <td>
                    {r.label}
                    {r.systemManaged ? (
                      <span className="ml-2 text-[10px] font-semibold uppercase text-amber-700">system</span>
                    ) : null}
                  </td>
                  <td className="font-mono text-xs">{r.fieldKey}</td>
                  <td>{r.inputType}</td>
                  <td>{r.required ? 'Yes' : '—'}</td>
                  <td>{r.active ? 'Yes' : 'No'}</td>
                  <td className="text-right">
                    <button type="button" className="mr-2 text-xs underline" onClick={() => openEdit(r)}>
                      Edit
                    </button>
                    {!r.systemManaged ? (
                      <button
                        type="button"
                        className="text-xs text-rose-700 underline"
                        onClick={() => void handleDelete(r)}
                      >
                        Delete
                      </button>
                    ) : null}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </BtCard>
      )}
    </div>
  );
}
