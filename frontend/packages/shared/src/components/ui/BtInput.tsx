import type { InputHTMLAttributes } from 'react';

type Props = InputHTMLAttributes<HTMLInputElement> & {
  label?: string;
};

export function BtInput({ label, className = '', id, ...rest }: Props) {
  const inputId = id ?? rest.name;
  return (
    <div className={className}>
      {label ? (
        <label htmlFor={inputId} className="bt-label">
          {label}
        </label>
      ) : null}
      <input id={inputId} className="bt-input" {...rest} />
    </div>
  );
}
