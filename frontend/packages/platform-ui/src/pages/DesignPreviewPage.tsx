import {
  BtBadge,
  BtButton,
  BtCard,
  BtCardHeader,
  BtInput,
  BtPageHeader,
  BtSelect,
  BtTextarea,
} from '@plp/shared';

export default function DesignPreviewPage() {
  return (
    <div className="space-y-8">
      <BtPageHeader
        title="BillionTech design preview"
        description="Shared primitives from @plp/shared — tokens from @billiontech/design-system."
      />

      <section>
        <h2 className="text-sm font-semibold text-[var(--bt-gray-700)] mb-3">Buttons</h2>
        <div className="flex flex-wrap gap-2">
          <BtButton>Primary</BtButton>
          <BtButton variant="secondary">Secondary</BtButton>
          <BtButton variant="danger">Danger</BtButton>
          <BtButton size="sm">Small</BtButton>
        </div>
      </section>

      <section>
        <h2 className="text-sm font-semibold text-[var(--bt-gray-700)] mb-3">Badges</h2>
        <div className="flex flex-wrap gap-2">
          <BtBadge tone="orange">Pending</BtBadge>
          <BtBadge tone="green">Active</BtBadge>
          <BtBadge tone="red">Overdue</BtBadge>
          <BtBadge tone="amber">Revision</BtBadge>
          <BtBadge tone="blue">Eligible</BtBadge>
          <BtBadge status="DISBURSED">DISBURSED</BtBadge>
        </div>
      </section>

      <section>
        <h2 className="text-sm font-semibold text-[var(--bt-gray-700)] mb-3">Form controls</h2>
        <div className="grid gap-4 max-w-md">
          <BtInput label="Email" type="email" placeholder="you@company.com" />
          <BtSelect label="Product">
            <option>Pay Day Loan</option>
            <option>Invoice Discounting</option>
          </BtSelect>
          <BtTextarea label="Notes" placeholder="Optional notes…" />
        </div>
      </section>

      <section>
        <h2 className="text-sm font-semibold text-[var(--bt-gray-700)] mb-3">Cards</h2>
        <BtCard>
          <BtCardHeader title="Sample card" actions={<BtButton size="sm">Action</BtButton>} />
          <p className="text-[13px] text-[var(--bt-gray-600)]">Card body using bt-card styles.</p>
        </BtCard>
      </section>

      <section>
        <h2 className="text-sm font-semibold text-[var(--bt-gray-700)] mb-3">Alerts</h2>
        <div className="space-y-2 max-w-lg">
          <div className="bt-alert bt-alert-success">Success message</div>
          <div className="bt-alert bt-alert-error">Error message</div>
          <div className="bt-alert bt-alert-warning">Warning message</div>
          <div className="bt-alert bt-alert-info">Info message</div>
        </div>
      </section>
    </div>
  );
}
