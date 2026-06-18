import type { TextareaHTMLAttributes } from 'react';

type Props = TextareaHTMLAttributes<HTMLTextAreaElement> & {
  label?: string;
};

export function BtTextarea({ label, className = '', id, ...rest }: Props) {
  const textareaId = id ?? rest.name;
  return (
    <div className={className}>
      {label ? (
        <label htmlFor={textareaId} className="bt-label">
          {label}
        </label>
      ) : null}
      <textarea id={textareaId} className="bt-input min-h-[88px] resize-y" {...rest} />
    </div>
  );
}
