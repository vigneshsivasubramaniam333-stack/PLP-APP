import type { HTMLAttributes, ReactNode } from 'react';
import { BrandLogo, BrandedAuthFrame, PortalSidebarBrand, PoweredByFooter } from './ui/BrandLogo';

export const COMPANY_LEGAL_NAME = 'Credinnov';
export const TECH_LEGAL_NAME = 'Credinnov';
export const POWERED_BY_LINE = `Powered by ${TECH_LEGAL_NAME}`;
export const BRAND_PRIMARY_LOGO_PATH = './assets/branding/BillionTech_Logo_Final.png';
export const BRAND_TECH_LOGO_PATH = './assets/branding/BillionTech_Logo_Final.png';

export { BrandLogo, PortalSidebarBrand, PoweredByFooter, BrandedAuthFrame };

type LoginBrandHeaderProps = {
  portalSubtitle?: string;
  strapline?: string;
};

export function PortalLoginBrandHeader({ portalSubtitle, strapline }: LoginBrandHeaderProps) {
  return (
    <div className="mb-6 flex flex-col items-center text-center">
      <BrandLogo variant="billiontech" tone="dark" height={38} />
      <p className="mt-3 text-sm font-semibold text-[var(--bt-gray-900)]">{COMPANY_LEGAL_NAME}</p>
      {portalSubtitle ? (
        <h1 className="mt-2 text-lg font-semibold text-[var(--bt-gray-700)]">{portalSubtitle}</h1>
      ) : null}
      {strapline ? <p className="mt-1 max-w-[280px] text-sm text-[var(--bt-gray-500)]">{strapline}</p> : null}
    </div>
  );
}

type LoginPoweredByProps = HTMLAttributes<HTMLDivElement>;

export function PortalLoginPoweredBy({ className = '', ...rest }: LoginPoweredByProps) {
  return <PoweredByFooter className={`mt-6 border-0 bg-transparent ${className}`} {...rest} />;
}

export function PortalPoweredByFooter(props: HTMLAttributes<HTMLElement>) {
  return <PoweredByFooter {...props} />;
}

type AuthShellProps = {
  children: ReactNode;
  title: string;
  subtitle: string;
};

export function PortalAuthShell({ children, title, subtitle }: AuthShellProps) {
  return (
    <BrandedAuthFrame title={title} subtitle={subtitle}>
      {children}
    </BrandedAuthFrame>
  );
}
