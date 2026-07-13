import { useCallback, useEffect, useId, useLayoutEffect, useRef, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';

type FieldTooltipProps = {
  text: string;
  className?: string;
};

type TipCoords = {
  top: number;
  left: number;
  placement: 'above' | 'below';
};

const TIP_GAP = 8;
const VIEWPORT_PAD = 8;
const ARROW_SIZE = 6;

/**
 * Brand-themed info icon; tip portals to document.body and clamps to the viewport.
 */
export function FieldTooltip({ text, className = '' }: FieldTooltipProps) {
  const tipId = useId();
  const triggerRef = useRef<HTMLButtonElement>(null);
  const tipRef = useRef<HTMLSpanElement>(null);
  const [open, setOpen] = useState(false);
  const [coords, setCoords] = useState<TipCoords | null>(null);

  const updatePosition = useCallback(() => {
    const trigger = triggerRef.current;
    const tip = tipRef.current;
    if (!trigger || !tip) return;

    const tr = trigger.getBoundingClientRect();
    const tipW = tip.offsetWidth;
    const tipH = tip.offsetHeight;
    const vw = window.innerWidth;
    const vh = window.innerHeight;

    const spaceBelow = vh - tr.bottom - VIEWPORT_PAD;
    const spaceAbove = tr.top - VIEWPORT_PAD;
    const preferBelow = spaceBelow >= tipH + TIP_GAP || spaceBelow >= spaceAbove;
    const placement: 'above' | 'below' = preferBelow ? 'below' : 'above';

    let top =
      placement === 'below' ? tr.bottom + TIP_GAP : tr.top - tipH - TIP_GAP;
    top = Math.max(VIEWPORT_PAD, Math.min(top, vh - tipH - VIEWPORT_PAD));

    let left = tr.left + tr.width / 2 - tipW / 2;
    left = Math.max(VIEWPORT_PAD, Math.min(left, vw - tipW - VIEWPORT_PAD));

    setCoords({ top, left, placement });
  }, []);

  useLayoutEffect(() => {
    if (!open) {
      setCoords(null);
      return;
    }
    updatePosition();
  }, [open, text, updatePosition]);

  useEffect(() => {
    if (!open) return;
    const onReposition = () => updatePosition();
    window.addEventListener('scroll', onReposition, true);
    window.addEventListener('resize', onReposition);
    return () => {
      window.removeEventListener('scroll', onReposition, true);
      window.removeEventListener('resize', onReposition);
    };
  }, [open, updatePosition]);

  const tip =
    open && typeof document !== 'undefined'
      ? createPortal(
          <span
            ref={tipRef}
            id={tipId}
            role="tooltip"
            style={
              coords
                ? { top: coords.top, left: coords.left, visibility: 'visible' as const }
                : { top: 0, left: 0, visibility: 'hidden' as const }
            }
            className="pointer-events-none fixed z-[9999] w-max max-w-xs rounded-lg border border-slate-200 bg-white px-3 py-2 text-left text-xs font-normal leading-snug text-slate-700 shadow-lg shadow-slate-900/10"
          >
            <span
              className="absolute left-1/2 h-0 w-0 -translate-x-1/2 border-x-[6px] border-x-transparent"
              style={
                coords?.placement === 'above'
                  ? {
                      bottom: -ARROW_SIZE,
                      borderTopWidth: ARROW_SIZE,
                      borderTopColor: 'rgb(226 232 240)', // slate-200
                    }
                  : {
                      top: -ARROW_SIZE,
                      borderBottomWidth: ARROW_SIZE,
                      borderBottomColor: 'rgb(226 232 240)',
                    }
              }
              aria-hidden
            />
            <span className="relative block border-l-2 border-[var(--bt-orange)] pl-2">{text}</span>
          </span>,
          document.body,
        )
      : null;

  return (
    <span
      className={`relative inline-flex align-middle ${className}`}
      onMouseEnter={() => setOpen(true)}
      onMouseLeave={() => setOpen(false)}
    >
      <button
        ref={triggerRef}
        type="button"
        className="inline-flex h-4 w-4 shrink-0 items-center justify-center rounded-full border border-slate-300 bg-slate-50 text-[10px] font-semibold leading-none text-slate-500 hover:border-[var(--bt-orange)] hover:bg-orange-50 hover:text-[var(--bt-orange)] focus:outline-none focus-visible:ring-2 focus-visible:ring-[var(--bt-orange)] focus-visible:ring-offset-1"
        aria-label="More information"
        aria-describedby={open ? tipId : undefined}
        onFocus={() => setOpen(true)}
        onBlur={() => setOpen(false)}
      >
        i
      </button>
      {tip}
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
