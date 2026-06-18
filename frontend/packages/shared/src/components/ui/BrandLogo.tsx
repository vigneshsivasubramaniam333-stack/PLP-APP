import type { ReactNode } from 'react';
import { BRAND_PRIMARY_LOGO_PATH, BRAND_TECH_LOGO_PATH } from '../portalBranding';

export type BrandLogoVariant = 'billionloans' | 'billiontech';
export type BrandLogoTone = 'light' | 'dark';

type Props = {
  variant?: BrandLogoVariant;
  tone?: BrandLogoTone;
  className?: string;
  height?: number;
};

export function BrandLogo({
  variant = 'billiontech',
  tone = 'dark',
  className = '',
  height,
}: Props) {
  const h = height ?? (className.includes('text-2xl') ? 40 : className.includes('text-xs') ? 14 : 28);
  const src =
    variant === 'billiontech'
      ? BRAND_TECH_LOGO_PATH
      : tone === 'light'
        ? './assets/branding/BillionLoans_Logo_Final.png'
        : BRAND_PRIMARY_LOGO_PATH;
  const alt = variant === 'billiontech' ? 'BillionTech' : 'Billionloans';

  return (
    <span className={['inline-flex items-center leading-none', className].filter(Boolean).join(' ')}>
      <img src={src} alt={alt} style={{ height: h, width: 'auto', objectFit: 'contain' }} />
    </span>
  );
}

type SidebarBrandProps = {
  portalTitle: string;
  metaLine?: string;
  className?: string;
};

export function PortalSidebarBrand({ portalTitle, metaLine, className = '' }: SidebarBrandProps) {
  return (
    <div className={['bt-sidebar-wide-header', className].filter(Boolean).join(' ')}>
      <BrandLogo variant="billiontech" tone="dark" height={26} />
      <p className="bt-sidebar-wide-subtitle">{portalTitle}</p>
      {metaLine ? (
        <p className="truncate text-[11px] text-[var(--bt-gray-400)]" title={metaLine}>
          {metaLine}
        </p>
      ) : null}
    </div>
  );
}

type PoweredByProps = {
  className?: string;
};

export function PoweredByFooter({ className = '' }: PoweredByProps) {
  return (
    <footer className={['bt-powered-by-footer shrink-0', className].filter(Boolean).join(' ')}>
      <div className="flex flex-wrap items-center justify-center gap-1.5">
        <span>Powered by</span>
        <BrandLogo variant="billiontech" tone="dark" height={12} />
      </div>
    </footer>
  );
}

type AuthFrameProps = {
  children: ReactNode;
  title: string;
  subtitle: string;
  companyName?: string;
  heroTitle?: string;
  heroText?: string;
};

export function BrandedAuthFrame({
  children,
  title,
  subtitle,
  companyName = 'Credinnov',
  heroTitle = 'Loans made simple',
  heroText = 'Secure access to your BillionTech lending workspace.',
}: AuthFrameProps) {
  return (
    <div className="bt-auth-split">
      <div className="bt-auth-panel">
        <div className="bt-auth-card">
          <div className="mb-6 text-center">
            <BrandLogo variant="billiontech" tone="dark" height={38} className="mx-auto" />
            <p className="mt-3 text-sm font-semibold text-[var(--bt-gray-900)]">{companyName}</p>
            <h1 className="mt-2 font-display text-lg font-bold text-[var(--bt-gray-900)]">{title}</h1>
            <p className="mt-1 text-sm text-[var(--bt-gray-500)]">{subtitle}</p>
          </div>
          {children}
          <PoweredByFooter className="mt-6 border-0 bg-transparent" />
        </div>
      </div>
      <div className="bt-auth-hero">
        <div className="max-w-sm text-center">
          <h2 className="font-display text-2xl font-extrabold text-[var(--bt-orange)]">{heroTitle}</h2>
          <p className="mt-3 text-sm leading-relaxed text-[var(--bt-gray-700)]">{heroText}</p>
        </div>
      </div>
    </div>
  );
}
