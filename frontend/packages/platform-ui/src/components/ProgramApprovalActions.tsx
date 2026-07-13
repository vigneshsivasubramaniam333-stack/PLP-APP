import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import {
  programApi,
  extractApiErrorMessage,
  notifySuccess,
  notifyError,
  BtButton,
  BtBadge,
  lenderLoanCapabilities,
  getStoredAuthUser,
} from '@plp/shared';
import type { Program, ProgramOperationalParameters } from '@plp/shared';

type MenuItem = {
  id: string;
  label: string;
  onClick: () => void;
  disabled?: boolean;
  tone?: 'default' | 'primary' | 'warning';
};

function ProgramActionsMenu({ items, busy }: { items: MenuItem[]; busy?: boolean }) {
  const [open, setOpen] = useState(false);
  const [menuPos, setMenuPos] = useState<{ top: number; left: number } | null>(null);
  const rootRef = useRef<HTMLDivElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);
  const menuWidth = 176;

  const closeMenu = () => {
    setOpen(false);
    setMenuPos(null);
  };

  const toggleMenu = () => {
    if (open) {
      closeMenu();
      return;
    }
    const btn = rootRef.current?.querySelector('button');
    if (btn) {
      const rect = btn.getBoundingClientRect();
      const left = Math.max(8, Math.min(rect.right - menuWidth, window.innerWidth - menuWidth - 8));
      // Tentative below; flipped after measure in effect when menu mounts.
      setMenuPos({ top: rect.bottom + 4, left });
    }
    setOpen(true);
  };

  useEffect(() => {
    if (!open || !menuPos) return;
    const btn = rootRef.current?.querySelector('button');
    if (!btn || !menuRef.current) return;
    const rect = btn.getBoundingClientRect();
    const menuHeight = menuRef.current.getBoundingClientRect().height;
    const gap = 4;
    const spaceBelow = window.innerHeight - rect.bottom - gap;
    const spaceAbove = rect.top - gap;
    let top = rect.bottom + gap;
    if (menuHeight > spaceBelow && spaceAbove > spaceBelow) {
      top = Math.max(8, rect.top - menuHeight - gap);
    } else if (top + menuHeight > window.innerHeight - 8) {
      top = Math.max(8, window.innerHeight - menuHeight - 8);
    }
    const left = Math.max(8, Math.min(rect.right - menuWidth, window.innerWidth - menuWidth - 8));
    if (top !== menuPos.top || left !== menuPos.left) {
      setMenuPos({ top, left });
    }
  }, [open, menuPos, items.length]);

  useEffect(() => {
    if (!open) return;
    const onDoc = (e: MouseEvent) => {
      const target = e.target as Node;
      if (rootRef.current?.contains(target) || menuRef.current?.contains(target)) return;
      closeMenu();
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') closeMenu();
    };
    document.addEventListener('mousedown', onDoc);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDoc);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  if (items.length === 0) {
    return <span className="text-xs text-slate-400">—</span>;
  }

  return (
    <>
      <div ref={rootRef} className="inline-flex justify-center">
        <button
          type="button"
          disabled={busy}
          onClick={toggleMenu}
          className="bt-btn bt-btn-secondary bt-btn-sm inline-flex items-center gap-1"
          aria-expanded={open}
          aria-haspopup="menu"
        >
          Actions
          <svg className="h-3.5 w-3.5 text-slate-500" viewBox="0 0 20 20" fill="currentColor" aria-hidden>
            <path
              fillRule="evenodd"
              d="M5.23 7.21a.75.75 0 011.06.02L10 10.94l3.71-3.71a.75.75 0 111.06 1.06l-4.24 4.25a.75.75 0 01-1.06 0L5.21 8.29a.75.75 0 01.02-1.08z"
              clipRule="evenodd"
            />
          </svg>
        </button>
      </div>
      {open && menuPos
        ? createPortal(
            <div
              ref={menuRef}
              role="menu"
              className="fixed z-[200] rounded-lg border border-slate-200 bg-white py-1 shadow-lg"
              style={{ top: menuPos.top, left: menuPos.left, width: menuWidth }}
            >
              {items.map((item) => (
                <button
                  key={item.id}
                  type="button"
                  role="menuitem"
                  disabled={item.disabled || busy}
                  onClick={() => {
                    closeMenu();
                    item.onClick();
                  }}
                  className={`block w-full px-3 py-2 text-left text-sm disabled:cursor-not-allowed disabled:opacity-50 ${
                    item.tone === 'primary'
                      ? 'text-[var(--bt-orange,#ea580c)] font-medium hover:bg-orange-50'
                      : item.tone === 'warning'
                        ? 'text-amber-800 hover:bg-amber-50'
                        : 'text-slate-700 hover:bg-slate-50'
                  }`}
                >
                  {item.label}
                </button>
              ))}
            </div>,
            document.body,
          )
        : null}
    </>
  );
}

type ApprovalConfig = {
  l1Role: string;
  l2Role: string;
  enabled: boolean;
};

function fmtStatusLabel(status: string): string {
  if (status === 'PENDING_L2') return 'Pending L2';
  if (status === 'SENT_BACK') return 'Sent back';
  return status;
}

function parseOperationalParams(p: Program): ProgramOperationalParameters {
  const o = (p.parameters ?? {}) as Record<string, unknown>;
  return o as ProgramOperationalParameters;
}

function ProgramReviewModal({ program, onClose }: { program: Program; onClose: () => void }) {
  const ops = parseOperationalParams(program);
  const cfg = (program.config ?? {}) as Record<string, unknown>;
  const rows: { label: string; value: string }[] = [
    { label: 'Program code', value: program.programCode },
    { label: 'Product', value: program.productType },
    { label: 'Program limit', value: `₹${Number(program.programLimit).toLocaleString('en-IN')}` },
    { label: 'Max. dealer limit', value: `₹${Number(program.maxBorrowerLimit).toLocaleString('en-IN')}` },
    { label: 'Interest rate', value: `${program.defaultInterestRate}% p.a.` },
    { label: 'Margin', value: program.marginPercent != null ? `${program.marginPercent}%` : '0%' },
    { label: 'Max tenure', value: `${program.maxTenureDays ?? '—'} days` },
    { label: 'Auto discounting', value: ops.autoDiscounting ? 'Yes' : 'No' },
    { label: 'Auto accept invoices', value: ops.autoAcceptInvoices ? 'Yes' : 'No' },
    { label: 'Sanction type', value: String(ops.sanctionType ?? 'MANUAL') },
    { label: 'LMS entry', value: program.lmsEntryIn === 'YES' ? `Yes (${program.encoreProductCode || '—'})` : 'No' },
  ];
  if (cfg.maxInvoiceAgeDays != null) rows.push({ label: 'Max invoice age', value: `${cfg.maxInvoiceAgeDays} days` });
  if (cfg.minInvoiceAmount != null) rows.push({ label: 'Min invoice amount', value: `₹${Number(cfg.minInvoiceAmount).toLocaleString('en-IN')}` });
  if (cfg.minDaysToDueDate != null) rows.push({ label: 'Min days to due', value: `${cfg.minDaysToDueDate} days` });

  return (
    <div className="bt-modal-overlay">
      <div className="bt-modal !max-w-2xl w-full" role="dialog" aria-modal="true">
        <div className="bt-modal-header">
          <h2 className="bt-modal-title">Review program parameters</h2>
          <button type="button" onClick={onClose} className="text-slate-400 hover:text-slate-600" aria-label="Close">
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>
        <div className="bt-modal-body">
          <div className="mb-3 flex flex-wrap items-center gap-2">
            <span className="font-semibold text-slate-800">{program.programName}</span>
            <BtBadge status={program.status}>{fmtStatusLabel(program.status)}</BtBadge>
          </div>
          {program.approvalRemarks ? (
            <div className="mb-4 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-950">
              <span className="font-medium">L2 remarks: </span>
              {program.approvalRemarks}
            </div>
          ) : null}
          <table className="w-full text-sm border border-slate-200 rounded-lg overflow-hidden">
            <tbody>
              {rows.map((r) => (
                <tr key={r.label} className="border-b border-slate-100 last:border-b-0">
                  <td className="px-3 py-2 text-slate-500 bg-slate-50 w-[40%]">{r.label}</td>
                  <td className="px-3 py-2 text-slate-800 font-medium">{r.value}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="bt-modal-footer">
          <button type="button" onClick={onClose} className="bt-btn bt-btn-secondary">
            Close
          </button>
        </div>
      </div>
    </div>
  );
}

function SendBackModal({
  program,
  busy,
  onClose,
  onSubmit,
}: {
  program: Program;
  busy: boolean;
  onClose: () => void;
  onSubmit: (remarks: string) => void;
}) {
  const [remarks, setRemarks] = useState('');
  return (
    <div className="bt-modal-overlay">
      <div className="bt-modal !max-w-lg w-full" role="dialog" aria-modal="true">
        <div className="bt-modal-header">
          <h2 className="bt-modal-title">Send back to L1</h2>
          <button type="button" onClick={onClose} className="text-slate-400 hover:text-slate-600" aria-label="Close">
            ×
          </button>
        </div>
        <div className="bt-modal-body">
          <p className="text-sm text-slate-600 mb-3">
            Send <strong>{program.programName}</strong> back to the L1 officer with remarks. They will be notified to
            revise parameters and resubmit.
          </p>
          <label className="bt-label">Remarks *</label>
          <textarea
            className="bt-input w-full min-h-[100px] resize-y"
            value={remarks}
            onChange={(e) => setRemarks(e.target.value)}
            placeholder="Describe what needs to be changed…"
            required
          />
        </div>
        <div className="bt-modal-footer">
          <button type="button" onClick={onClose} className="bt-btn bt-btn-secondary" disabled={busy}>
            Cancel
          </button>
          <BtButton
            type="button"
            disabled={busy || !remarks.trim()}
            onClick={() => onSubmit(remarks.trim())}
          >
            {busy ? 'Sending…' : 'Send back'}
          </BtButton>
        </div>
      </div>
    </div>
  );
}

function SendBackToRmModal({
  program,
  busy,
  onClose,
  onSubmit,
}: {
  program: Program;
  busy: boolean;
  onClose: () => void;
  onSubmit: (remarks: string) => void;
}) {
  const [remarks, setRemarks] = useState('');
  return (
    <div className="bt-modal-overlay">
      <div className="bt-modal !max-w-lg w-full" role="dialog" aria-modal="true">
        <div className="bt-modal-header">
          <h2 className="bt-modal-title">Send back to RM</h2>
          <button type="button" onClick={onClose} className="text-slate-400 hover:text-slate-600" aria-label="Close">
            ×
          </button>
        </div>
        <div className="bt-modal-body">
          <p className="text-sm text-slate-600 mb-3">
            Send <strong>{program.programName}</strong> back to the relationship manager for revision. Remarks are
            optional.
          </p>
          <label className="bt-label">Remarks (optional)</label>
          <textarea
            className="bt-input w-full min-h-[100px] resize-y"
            value={remarks}
            onChange={(e) => setRemarks(e.target.value)}
            placeholder="Optional notes for the RM…"
          />
        </div>
        <div className="bt-modal-footer">
          <button type="button" onClick={onClose} className="bt-btn bt-btn-secondary" disabled={busy}>
            Cancel
          </button>
          <BtButton type="button" disabled={busy} onClick={() => onSubmit(remarks.trim())}>
            {busy ? 'Sending…' : 'Send back to RM'}
          </BtButton>
        </div>
      </div>
    </div>
  );
}

function ApprovalConfigModal({
  config,
  busy,
  onClose,
  onSave,
}: {
  config: ApprovalConfig;
  busy: boolean;
  onClose: () => void;
  onSave: (cfg: ApprovalConfig) => void;
}) {
  const [form, setForm] = useState(config);
  return (
    <div className="bt-modal-overlay">
      <div className="bt-modal !max-w-md w-full" role="dialog" aria-modal="true">
        <div className="bt-modal-header">
          <h2 className="bt-modal-title">Program approval flow</h2>
          <button type="button" onClick={onClose} className="text-slate-400 hover:text-slate-600" aria-label="Close">
            ×
          </button>
        </div>
        <div className="bt-modal-body space-y-4">
          <p className="text-sm text-slate-600">
            Configure which roles act as L1 (submit) and L2 (approve / send back). Approvals happen on this Programs
            screen — no separate workbench needed.
          </p>
          <div>
            <label className="bt-label">L1 role (submit for review)</label>
            <select
              className="bt-input w-full"
              value={form.l1Role}
              onChange={(e) => setForm({ ...form, l1Role: e.target.value })}
            >
              <option value="CREDIT_ANALYST">Credit Officer (CREDIT_ANALYST)</option>
              <option value="CREDIT_MANAGER">Credit Manager</option>
            </select>
          </div>
          <div>
            <label className="bt-label">L2 role (approve / send back)</label>
            <select
              className="bt-input w-full"
              value={form.l2Role}
              onChange={(e) => setForm({ ...form, l2Role: e.target.value })}
            >
              <option value="CREDIT_MANAGER">Credit Manager</option>
              <option value="CREDIT_ANALYST">Credit Officer (CREDIT_ANALYST)</option>
            </select>
          </div>
          <label className="inline-flex items-center gap-2 text-sm text-slate-700">
            <input
              type="checkbox"
              checked={form.enabled}
              onChange={(e) => setForm({ ...form, enabled: e.target.checked })}
              className="h-4 w-4 rounded border-slate-300"
            />
            Two-step approval enabled
          </label>
        </div>
        <div className="bt-modal-footer">
          <button type="button" onClick={onClose} className="bt-btn bt-btn-secondary" disabled={busy}>
            Cancel
          </button>
          <BtButton type="button" disabled={busy} onClick={() => onSave(form)}>
            {busy ? 'Saving…' : 'Save'}
          </BtButton>
        </div>
      </div>
    </div>
  );
}

export function ProgramApprovalToolbar({
  isPlatformAdmin,
  sentBackCount,
  onOpenConfig,
}: {
  isPlatformAdmin: boolean;
  sentBackCount: number;
  onOpenConfig: () => void;
}) {
  if (!isPlatformAdmin && sentBackCount === 0) return null;
  return (
    <div className="mb-4 flex flex-wrap items-center gap-3">
      {sentBackCount > 0 ? (
        <div className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-950">
          {sentBackCount} program{sentBackCount === 1 ? '' : 's'} sent back — review remarks, update parameters, then
          resubmit for L2.
        </div>
      ) : null}
      {isPlatformAdmin ? (
        <button type="button" onClick={onOpenConfig} className="bt-btn bt-btn-secondary bt-btn-sm">
          Approval flow settings
        </button>
      ) : null}
    </div>
  );
}

export function ProgramApprovalActions({
  program,
  approvalConfig,
  onChanged,
  onEdit,
  canEdit,
}: {
  program: Program;
  approvalConfig: ApprovalConfig;
  onChanged: () => void;
  onEdit?: () => void;
  canEdit?: boolean;
}) {
  const role = getStoredAuthUser()?.role;
  const caps = lenderLoanCapabilities(role);
  const [busy, setBusy] = useState(false);
  const [review, setReview] = useState(false);
  const [sendBack, setSendBack] = useState(false);
  const [sendBackToRm, setSendBackToRm] = useState(false);

  const isL1 = role === approvalConfig.l1Role || role === 'PLATFORM_ADMIN';
  const isL2 = role === approvalConfig.l2Role || role === 'PLATFORM_ADMIN';
  const st = program.status;

  async function run(action: () => Promise<unknown>, successMsg: string) {
    setBusy(true);
    try {
      await action();
      notifySuccess(successMsg);
      onChanged();
    } catch (e) {
      notifyError(e, 'Action failed');
    } finally {
      setBusy(false);
    }
  }

  const menuItems: MenuItem[] = [{ id: 'review', label: 'Review parameters', onClick: () => setReview(true) }];

  if (canEdit && onEdit) {
    menuItems.push({ id: 'edit', label: 'Edit program', onClick: onEdit });
  }

  if (approvalConfig.enabled && isL1 && (st === 'DRAFT' || st === 'SENT_BACK')) {
    menuItems.push({
      id: 'submit-l2',
      label: 'Submit for L2',
      tone: 'primary',
      disabled: busy,
      onClick: () => void run(() => programApi.submitForL2(program.id), 'Submitted for L2 review'),
    });
  }

  if (approvalConfig.enabled && isL1 && st === 'DRAFT') {
    menuItems.push({
      id: 'send-back-to-rm',
      label: 'Send back to RM',
      tone: 'warning',
      disabled: busy,
      onClick: () => setSendBackToRm(true),
    });
  }

  if (approvalConfig.enabled && isL2 && st === 'PENDING_L2') {
    menuItems.push({
      id: 'approve',
      label: 'Approve program',
      tone: 'primary',
      disabled: busy,
      onClick: () => void run(() => programApi.approveL2(program.id), 'Program approved'),
    });
    menuItems.push({
      id: 'send-back',
      label: 'Send back to L1',
      tone: 'warning',
      disabled: busy,
      onClick: () => setSendBack(true),
    });
  }

  if (!approvalConfig.enabled && caps.canApproveProgramArtifacts && st === 'DRAFT') {
    menuItems.push({
      id: 'activate',
      label: 'Activate program',
      tone: 'primary',
      disabled: busy,
      onClick: () => void run(() => programApi.updateStatus(program.id, 'ACTIVE'), 'Program activated'),
    });
  }

  return (
    <>
      <ProgramActionsMenu items={menuItems} busy={busy} />
      {review ? <ProgramReviewModal program={program} onClose={() => setReview(false)} /> : null}
      {sendBack ? (
        <SendBackModal
          program={program}
          busy={busy}
          onClose={() => setSendBack(false)}
          onSubmit={async (remarks) => {
            await run(() => programApi.sendBack(program.id, remarks), 'Sent back to L1');
            setSendBack(false);
          }}
        />
      ) : null}
      {sendBackToRm ? (
        <SendBackToRmModal
          program={program}
          busy={busy}
          onClose={() => setSendBackToRm(false)}
          onSubmit={async (remarks) => {
            await run(() => programApi.sendBackToRm(program.id, remarks || undefined), 'Sent back to RM');
            setSendBackToRm(false);
          }}
        />
      ) : null}
    </>
  );
}

export function useProgramApprovalConfig() {
  const [config, setConfig] = useState<ApprovalConfig>({
    l1Role: 'CREDIT_ANALYST',
    l2Role: 'CREDIT_MANAGER',
    enabled: true,
  });
  const [showConfig, setShowConfig] = useState(false);
  const [configSaving, setConfigSaving] = useState(false);

  useEffect(() => {
    programApi
      .getApprovalConfig()
      .then((res) => {
        const d = res.data?.data as ApprovalConfig | undefined;
        if (d) setConfig({ l1Role: d.l1Role, l2Role: d.l2Role, enabled: d.enabled });
      })
      .catch(() => undefined);
  }, []);

  async function saveConfig(cfg: ApprovalConfig) {
    setConfigSaving(true);
    try {
      const res = await programApi.updateApprovalConfig(cfg);
      const d = res.data?.data as ApprovalConfig;
      setConfig({ l1Role: d.l1Role, l2Role: d.l2Role, enabled: d.enabled });
      notifySuccess('Approval flow settings saved');
      setShowConfig(false);
    } catch (e) {
      notifyError(e, extractApiErrorMessage(e, 'Failed to save settings'));
    } finally {
      setConfigSaving(false);
    }
  }

  const configModal = showConfig ? (
    <ApprovalConfigModal
      config={config}
      busy={configSaving}
      onClose={() => setShowConfig(false)}
      onSave={(cfg) => void saveConfig(cfg)}
    />
  ) : null;

  return { config, configModal, openConfig: () => setShowConfig(true) };
}
