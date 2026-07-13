export type BtButtonVariant = 'primary' | 'secondary' | 'danger';
export type BtButtonSize = 'default' | 'sm';

export function btButtonClass(
  variant: BtButtonVariant = 'primary',
  size: BtButtonSize = 'default',
  className = '',
): string {
  const base = 'bt-btn';
  const variantClass =
    variant === 'primary'
      ? 'bt-btn-primary'
      : variant === 'danger'
        ? 'bt-btn-danger'
        : 'bt-btn-secondary';
  const sizeClass = size === 'sm' ? 'bt-btn-sm' : '';
  return [base, variantClass, sizeClass, className].filter(Boolean).join(' ');
}

export type BadgeTone = 'orange' | 'green' | 'red' | 'amber' | 'blue' | 'gray';

const STATUS_COLOR: Record<string, BadgeTone> = {
  PAID: 'green',
  ACTIVE: 'green',
  COMPLETED: 'green',
  APPROVED: 'green',
  DISBURSED: 'green',
  CLOSED: 'green',
  PENDING: 'orange',
  PENDING_APPROVAL: 'orange',
  PENDING_L2: 'orange',
  DRAFT: 'amber',
  SENT_BACK: 'amber',
  DUE: 'orange',
  ELIGIBLE: 'blue',
  OVERDUE: 'red',
  CANCELLED: 'red',
  REJECTED: 'red',
  FAILED: 'red',
  VIEWED: 'blue',
  REVISION_REQUESTED: 'amber',
  WAIVED: 'amber',
};

export function badgeToneForStatus(status: string | null | undefined): BadgeTone {
  if (!status) return 'gray';
  const key = status.trim().toUpperCase().replace(/[\s-]+/g, '_');
  return STATUS_COLOR[key] ?? 'gray';
}

export function btBadgeClass(tone: BadgeTone, className = ''): string {
  return ['bt-badge', `bt-badge-${tone}`, className].filter(Boolean).join(' ');
}
