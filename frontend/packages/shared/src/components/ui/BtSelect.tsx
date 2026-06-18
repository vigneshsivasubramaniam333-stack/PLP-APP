import type { SelectHTMLAttributes } from 'react';

type Props = SelectHTMLAttributes<HTMLSelectElement> & {
  label?: string;
};

export function BtSelect({ label, className = '', id, children, ...rest }: Props) {
  const selectId = id ?? rest.name;
  return (
    <div className={className}>
      {label ? (
        <label htmlFor={selectId} className="bt-label">
          {label}
        </label>
      ) : null}
      <select id={selectId} className="bt-input" {...rest}>
        {children}
      </select>
    </div>
  );
}
