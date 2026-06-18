import type { ButtonHTMLAttributes, ReactNode } from 'react';
import { btButtonClass, type BtButtonSize, type BtButtonVariant } from './btUtils';

type Props = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: BtButtonVariant;
  size?: BtButtonSize;
  children: ReactNode;
};

export function BtButton({
  variant = 'primary',
  size = 'default',
  className = '',
  type = 'button',
  children,
  ...rest
}: Props) {
  return (
    <button type={type} className={btButtonClass(variant, size, className)} {...rest}>
      {children}
    </button>
  );
}
