import type { HTMLAttributes } from 'react';

export const COMPANY_LEGAL_NAME = 'Billionloans Financial Services Pvt Ltd';
export const TECH_LEGAL_NAME = 'Billionloans Technology Services Pvt Ltd';
export const POWERED_BY_LINE = `Powered by ${TECH_LEGAL_NAME}`;
export const BRAND_PRIMARY_LOGO_PATH = './assets/branding/BillionLoans_Logo_Final_noBG.png';
export const BRAND_TECH_LOGO_PATH = './assets/branding/BillionTech_Logo_Final.png';

type SidebarBrandProps = {
  portalTitle: string;
  metaLine?: string;
  className?: string;
};

export function PortalSidebarBrand({ portalTitle, metaLine, className = '' }: SidebarBrandProps) {
  return (
    <div className={`px-4 py-4 ${className}`}>
      <div className="flex items-center">
        <img
          src={BRAND_PRIMARY_LOGO_PATH}
          alt=""
          aria-hidden
          style={{ height: '26px', width: 'auto', display: 'block' }}
        />
      </div>
      <div className="mt-3 space-y-0.5">
        <p className="text-[13px] font-semibold text-slate-200">{portalTitle}</p>
        {metaLine ? (
          <p className="truncate text-[11px] text-slate-400" title={metaLine}>
            {metaLine}
          </p>
        ) : null}
      </div>
    </div>
  );
}

type LoginBrandHeaderProps = {
  portalSubtitle?: string;
  strapline?: string;
};

export function PortalLoginBrandHeader({ portalSubtitle, strapline }: LoginBrandHeaderProps) {
  return (
    <div className="mb-6 flex flex-col items-center text-center">
      <img
        src={BRAND_PRIMARY_LOGO_PATH}
        alt=""
        aria-hidden
        style={{ height: '38px', width: 'auto', display: 'block', margin: '0 auto 12px' }}
      />
      <p className="px-2 text-base font-semibold leading-tight text-white">{COMPANY_LEGAL_NAME}</p>
      {portalSubtitle ? (
        <h1 className="mt-3 text-lg font-semibold tracking-tight text-slate-300">{portalSubtitle}</h1>
      ) : null}
      {strapline ? <p className="mt-1 max-w-[280px] text-sm text-slate-400">{strapline}</p> : null}
    </div>
  );
}

type LoginPoweredByProps = HTMLAttributes<HTMLDivElement>;

export function PortalLoginPoweredBy({ className = '', ...rest }: LoginPoweredByProps) {
  return (
    <div
      className={`mx-auto mt-6 flex flex-col items-center gap-2 text-center opacity-70 ${className}`}
      {...rest}
    >
      <div className="flex max-w-[min(100%,20rem)] flex-wrap items-center justify-center gap-x-2 gap-y-1.5">
        <span className="text-[11px] leading-snug text-[#666]">Powered by</span>
        <img
          src={BRAND_TECH_LOGO_PATH}
          alt=""
          aria-hidden
          style={{ height: '12px', width: 'auto', display: 'block' }}
        />
        <span className="text-[11px] leading-snug text-[#666]">{TECH_LEGAL_NAME}</span>
      </div>
    </div>
  );
}

type PoweredByFooterProps = HTMLAttributes<HTMLElement>;

export function PortalPoweredByFooter({ className = '', ...rest }: PoweredByFooterProps) {
  return (
    <footer
      className={`border-t border-slate-200/60 bg-slate-50 shrink-0 ${className}`}
      {...rest}
    >
      <div className="flex flex-wrap items-center justify-center gap-1.5 py-1.5 text-[10px] leading-snug text-slate-400">
        <span>Powered by</span>
        <img
          src={BRAND_TECH_LOGO_PATH}
          alt=""
          aria-hidden
          style={{ height: '12px', width: 'auto', display: 'block' }}
        />
        <span>{TECH_LEGAL_NAME}</span>
      </div>
    </footer>
  );
}
