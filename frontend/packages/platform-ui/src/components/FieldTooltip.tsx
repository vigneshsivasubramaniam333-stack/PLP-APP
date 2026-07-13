import { useId, useState, type ReactNode } from 'react';

type FieldTooltipProps = {
  text: string;
  className?: string;
};

/**
 * Small slate-themed info icon that shows a short tip on hover/focus.
 */
export function FieldTooltip({ text, className = '' }: FieldTooltipProps) {
  const tipId = useId();
  const [open, setOpen] = useState(false);

  return (
    <span
      className={`relative inline-flex align-middle ${className}`}
      onMouseEnter={() => setOpen(true)}
      onMouseLeave={() => setOpen(false)}
    >
      <button
        type="button"
        className="inline-flex h-4 w-4 shrink-0 items-center justify-center rounded-full border border-slate-300 bg-slate-50 text-[10px] font-semibold leading-none text-slate-500 hover:border-slate-400 hover:bg-slate-100 hover:text-slate-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-slate-400 focus-visible:ring-offset-1"
        aria-label="More information"
        aria-describedby={open ? tipId : undefined}
        onFocus={() => setOpen(true)}
        onBlur={() => setOpen(false)}
      >
        i
      </button>
      {open ? (
        <span
          id={tipId}
          role="tooltip"
          className="absolute left-1/2 top-full z-50 mt-1.5 w-max max-w-[220px] -translate-x-1/2 rounded-md border border-slate-200 bg-white px-2.5 py-1.5 text-left text-[11px] font-normal leading-snug text-slate-600 shadow-md"
        >
          {text}
        </span>
      ) : null}
    </span>
  );
}

/** Label row with optional tooltip icon beside the label text. */
export function FieldLabelWithTooltip({
  label,
  tooltip,
  className = 'bt-label',
}: {
  label: ReactNode;
  tooltip?: string;
  className?: string;
}) {
  return (
    <span className={`inline-flex items-center gap-1.5 ${className}`}>
      <span>{label}</span>
      {tooltip ? <FieldTooltip text={tooltip} /> : null}
    </span>
  );
}
