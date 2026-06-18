# BillionTech UI Unification Plan

**Document version:** 1.0  
**Date:** June 2026  
**Status:** Approved — implemented June 2026  
**Applies to:** Payment-Terms Engine, LOS (ui-service), PLP (platform-ui, borrower-portal, anchor-portal)

---

## 1. Executive summary

LOS, PLP (three portals), and Payment-Terms Engine currently look like three unrelated products: different primary colors, typography, sidebars, buttons, and form styles. Payment-Terms already implements the **BillionTech Design System** (`bt-*` tokens, orange primary, Inter / Plus Jakarta Sans).

This plan unifies **look and feel only** across all apps so users perceive one organization (BillionTech / BillionLoans), while **preserving every existing flow** — routes, APIs, auth, workflows, and page structure remain unchanged.

**Approach:** Extract Payment-Terms design tokens into a shared layer; reskin existing layouts; migrate pages incrementally via shared UI primitives.

**Estimated duration:** 7–8 weeks (1 developer) or 4–5 weeks (2 developers in parallel).

---

## 2. Background

### 2.1 Problem statement

| App | Primary color | Sidebar | Typography | Component system |
|-----|---------------|---------|------------|------------------|
| Payment-Terms | Orange `#F97316` | White top nav + 52px icon rail | Inter 13px + Plus Jakarta Sans | `bt-*` CSS classes |
| LOS | Blue `#1890ff` | Navy `#001529`, 240px text sidebar | Inter | Ad-hoc Tailwind (`bl-*`) |
| PLP Admin | Blue `#2563eb` | `--brand-navy` `#0f2042` | IBM Plex Sans | Ad-hoc + inline styles |
| PLP Borrower | Sky `#0284c7` | `slate-900` | IBM Plex Sans | Ad-hoc |
| PLP Anchor | Emerald `#059669` | `slate-900` | IBM Plex Sans | Ad-hoc |

Users moving between lending (LOS/PLP) and finance (Payment-Terms) experience inconsistent branding, density, and interaction patterns.

### 2.2 Reference application

**Canonical source:** `C:\Users\User\git\payment-terms-engine\frontend\src\index.css`

Key reference files:

| File | Purpose |
|------|---------|
| `frontend/src/index.css` | Design tokens (`--bt-*`), component classes |
| `frontend/tailwind.config.js` | Orange `primary` scale, font families |
| `frontend/src/components/common/Layout.js` | App shell (reference only — not copied structurally) |
| `frontend/src/pages/Login.js` | Auth visual pattern |
| `frontend/src/utils/helpers.js` | Status → badge color mapping |

---

## 3. Goals and non-goals

### 3.1 Goals

- [ ] Single BillionTech visual language: orange primary, shared neutrals, typography, cards, buttons, inputs, tables, badges
- [ ] Consistent BillionLoans / BillionTech branding (logo, favicon, powered-by footer)
- [ ] One maintainable token + component layer consumed by all frontends
- [ ] Incremental rollout with no big-bang UI rewrite

### 3.2 Non-goals (explicit)

- **No** route, navigation tree, or menu item changes
- **No** API, auth, or business-logic changes
- **No** copying Payment-Terms top-nav + icon-rail layout into LOS/PLP
- **No** dark mode (Payment-Terms is light-only; stay consistent)
- **No** forced migration to Lucide icons in phase 1 (optional later)

### 3.3 Guiding principle

> **Same skin, different skeleton.**  
> Adopt tokens and components from Payment-Terms. Keep each app’s information architecture (wide text sidebars for LOS/PLP; icon rail for Payment-Terms).

---

## 4. Target design specification

### 4.1 Color tokens

| Token | Hex | Usage |
|-------|-----|-------|
| `--bt-orange` | `#F97316` | Primary buttons, active nav, links, focus |
| `--bt-orange-hover` | `#EA6C0A` | Button hover |
| `--bt-orange-light` | `#FFF7ED` | Active nav background |
| `--bt-orange-border` | `#FDBA74` | Accent borders |
| `--bt-gray-50` | `#F8F9FB` | Page canvas |
| `--bt-gray-100` | `#F1F3F5` | Subtle dividers |
| `--bt-gray-200` | `#E5E7EB` | Card / input borders |
| `--bt-gray-300` | `#D1D5DB` | Input borders |
| `--bt-gray-500` | `#6B7280` | Muted text |
| `--bt-gray-700` | `#374151` | Body text |
| `--bt-gray-900` | `#111827` | Headings |
| `--bt-green` / `--bt-red` / `--bt-amber` / `--bt-blue` | (see Payment-Terms) | Status badges only |

### 4.2 Typography

| Element | Font | Size | Weight |
|---------|------|------|--------|
| Body | Inter | 13px | 400 |
| Page title | Plus Jakarta Sans | 18px | 700 |
| Card title | Inter | 13px | 600 |
| Form label | Inter | 11.5px | 600 (uppercase) |
| Table header | Inter | 10.5px | 600 (uppercase) |
| Button | Inter | 12.5px | 600 |

### 4.3 Component standards

| Component | Specification |
|-----------|---------------|
| Card (`.bt-card`) | White bg, 1px `#E5E7EB`, radius 10px |
| Button primary | Orange bg, white text, radius 7px, hover `#EA6C0A` |
| Button secondary | White bg, gray border |
| Input | Radius 7px; focus border orange + `rgba(249,115,22,0.12)` ring |
| Badge | Pill radius 20px; semantic colors via `.bt-badge-*` |
| Table | Compact; uppercase gray headers; row hover `#F8F9FB` |
| Modal | Radius 12px; shadow `0 20px 60px rgba(0,0,0,0.18)` |

### 4.4 Layout adaptation per app

| App | Structure (unchanged) | Visual reskin |
|-----|----------------------|---------------|
| Payment-Terms | Top nav 52px + icon sidebar 52px | Import shared tokens (already aligned) |
| LOS staff | Fixed 240px sidebar + sticky header | Navy sidebar → white + gray border; blue → orange |
| LOS borrower portal | Same shell as staff | Same reskin |
| LOS intake | Minimal top bar | BT header + form styles |
| PLP platform admin | 256px grouped sidebar + page header | Replace `--brand-navy` chrome with BT white sidebar |
| PLP borrower | 256px sidebar + user footer | Remove `slate-900` / sky accents |
| PLP anchor | Same as borrower | Remove `slate-900` / emerald accents |

**Portal differentiation:** Subtitle under logo only (“Platform Admin”, “Borrower Portal”, “Anchor Portal”). Same orange primary across all portals.

### 4.5 Brand hierarchy

| Context | Asset / label |
|---------|---------------|
| Customer-facing product | **BillionLoans** logo |
| Internal / powered-by | **BillionTech** logo + text |
| Legal entity label (where shown today) | **Credinnov** (unchanged) |

---

## 5. Technical architecture

### 5.1 Proposed package structure

```
billiontech-design-system/          # New shared folder or private npm package
├── tokens.css                      # :root --bt-* variables
├── components.css                  # .bt-btn, .bt-card, .bt-input, .bt-table, ...
├── fonts.css                       # Google Fonts: Inter + Plus Jakarta Sans
├── tailwind-v4-bridge.css          # @theme mapping for LOS + PLP (Tailwind v4)
├── tailwind-v3-bridge.css          # Optional bridge for Payment-Terms (Tailwind v3)
└── README.md

@billiontech/ui/                    # Phase 2 — React primitives (optional package)
├── BrandLogo.tsx
├── BtButton.tsx
├── BtInput.tsx
├── BtCard.tsx
├── BtBadge.tsx
├── BtPageHeader.tsx
├── BtTable.tsx
├── BrandedAuthFrame.tsx
└── PoweredByFooter.tsx
```

### 5.2 Consumption model

| Repository | Import path (initial) | Tailwind version |
|------------|----------------------|------------------|
| Payment-Terms | Relative copy or npm link | v3 |
| LOS `ui-service` | Relative copy or npm link | v4 |
| PLP `frontend/packages/*` | Via `@plp/shared` re-export | v4 |

### 5.3 Backward-compatibility shims (temporary)

During migration, map legacy tokens to BT tokens so existing pages render orange without line-by-line edits:

**LOS (`index.css`):**

```css
@theme {
  --color-bl-primary: var(--bt-orange);
  --color-bl-navy: var(--bt-gray-900);
  --color-bl-canvas: var(--bt-gray-50);
}
```

**PLP (`index.css`):**

```css
:root {
  --brand-navy: var(--bt-gray-900);
  --brand-accent: var(--bt-orange);
  --surface: var(--bt-gray-50);
}
```

Remove shims in Phase 5 after full migration.

---

## 6. Implementation phases

### Phase 0 — Foundation (3–4 days)

**Objective:** Shared token layer; all apps compile with BT colors via aliases.

| # | Task | Owner | Deliverable |
|---|------|-------|-------------|
| 0.1 | Extract `tokens.css` + `components.css` from Payment-Terms | Dev A | `design-system/` folder |
| 0.2 | Add Tailwind v4 `@theme` bridge | Dev A | `tailwind-v4-bridge.css` |
| 0.3 | Wire into LOS `ui-service/src/index.css` | Dev A | LOS builds; `bl-*` → orange |
| 0.4 | Wire into PLP shared + 3 portal `index.css` | Dev B | PLP builds |
| 0.5 | Standardize fonts in all `index.html` | Dev B | Inter + Plus Jakarta Sans |
| 0.6 | Verify favicons / logo assets in `public/` | Dev B | No broken brand images |

**Acceptance criteria:**

- [ ] All five frontends build without errors
- [ ] `bg-bl-primary` / primary buttons render orange on LOS
- [ ] No functional tests broken

---

### Phase 1 — Shared UI primitives (4–5 days)

**Objective:** Replace ad-hoc styling with reusable presentational components.

| Component | Replaces | Notes |
|-----------|----------|-------|
| `BrandLogo` | LOS `BrandLogo.tsx`, PLP `portalBranding.tsx` | variant: billionloans / billiontech; tone: light / dark |
| `BtButton` | `bg-bl-primary`, `bg-sky-600`, inline `#2563EB` | variants: primary, secondary, danger, sm |
| `BtInput`, `BtSelect`, `BtTextarea` | Scattered form classes | |
| `BtCard`, `BtCardHeader` | `rounded-xl border shadow-sm` copies | |
| `BtBadge` | Per-page status maps | Use Payment-Terms `STATUS_COLOR` logic |
| `BtPageHeader` | LOS `PageHeader`, PLP page titles | |
| `BtTable` | Table markup + header classes | |
| `BrandedAuthFrame` | LOS auth frame, PLP login pages | Visual only |
| `PoweredByFooter` | PLP footer, LOS footer | |

**Location:** Extend `PLP/packages/shared` and mirror imports in LOS (or publish `@billiontech/ui`).

**Acceptance criteria:**

- [ ] Primitive showcase page or Storybook documents all variants
- [ ] Components have zero routing / API logic

---

### Phase 2 — Layout reskin (5–6 days)

**Objective:** App shells match BillionTech; navigation structure unchanged.

#### LOS files

| File | Visual change | Must not change |
|------|---------------|-----------------|
| `layouts/MainLayout.tsx` | White sidebar, orange active nav | Nav links, RBAC, logout |
| `layouts/BorrowerPortalLayout.tsx` | Same reskin | Notifications, dynamic nav |
| `layouts/BorrowerLayout.tsx` | BT header | Intake routes |
| `components/BrandedAuthFrame.tsx` | Split login (Payment-Terms pattern) | Auth handlers |

#### PLP files

| File | Visual change | Must not change |
|------|---------------|-----------------|
| `platform-ui/layouts/MainLayout.tsx` | BT sidebar + header | Nav groups, admin routes |
| `borrower-portal/layouts/BorrowerLayout.tsx` | BT tokens; remove sky | Nav, credit limits |
| `anchor-portal/layouts/AnchorLayout.tsx` | BT tokens; remove emerald | Anchor nav |
| `platform-ui/pages/LoginPage.tsx` | `BrandedAuthFrame` | Auth API |
| `shared/components/portalBranding.tsx` | Deprecate → `BrandLogo` | Logo paths |

**Acceptance criteria:**

- [ ] Login → dashboard works in all 6 shells (LOS × 3 layouts, PLP × 3 portals)
- [ ] Screenshot review approved by product owner

---

### Phase 3 — High-traffic pages (8–10 days)

**Objective:** Most-used screens fully migrated.

#### Priority matrix

| Priority | LOS pages | PLP pages |
|----------|-----------|-----------|
| **P0** | `/login`, `/borrower/login` | All 3 login pages |
| **P1** | Dashboard, Applications list | Platform dashboard, Programs |
| **P2** | Application detail, Borrower dashboard | Borrower / anchor dashboards |
| **P3** | Invoice discounting, KYC / underwriting queues | Loans, invoices, credit limits |
| **P4** | Workflows, PLP sync, admin config | Admin config, user management |

#### Per-page migration checklist

1. Replace page title with `BtPageHeader`
2. Wrap panels in `BtCard`
3. Apply `BtTable` / `bt-table` classes to data grids
4. Replace buttons with `BtButton`
5. Replace status pills with `BtBadge`
6. Remove non-semantic `bg-sky-*`, `bg-emerald-*`, `#1890ff`, `#2563eb`

**Acceptance criteria:**

- [ ] P0–P2 complete in all apps
- [ ] Smoke tests pass (see §8)

---

### Phase 4 — Remaining surfaces & polish (8–10 days)

| Area | Work |
|------|------|
| LOS intake wizards | Replace `bg-slate-900` primary with orange (`intakeStepLayout.ts`) |
| Workflow editor, CAM, detail tabs | Card / table / badge sweep |
| Modals & dropdowns | BT overlay + shadow |
| Empty / loading states | `bt-skeleton` or consistent pulse |
| Toasts | Radius 8–10px, Inter 13px |
| Payment-Terms | Point at shared `tokens.css` (optional sync) |

**Acceptance criteria:**

- [ ] No primary CTAs use legacy blue / sky / emerald
- [ ] Grep audit clean (see §8.3)

---

### Phase 5 — Cleanup & documentation (2–3 days)

| Task | Deliverable |
|------|-------------|
| Remove `bl-*`, unused `--brand-mid`, compat shims | Clean `index.css` files |
| Consolidate PLP CSS to single import | Delete triplicated portal CSS |
| Author `DESIGN_SYSTEM.md` | Token reference, component usage, do/don't |
| Visual regression baseline | Screenshots per app (key pages) |
| Update Deployment Guide if asset paths change | Docs PR |

---

## 7. Timeline

```
Week 1   │ Phase 0 Foundation + Phase 2 LOS layout spike (proof of concept)
Week 2   │ Phase 1 Primitives + Phase 2 PLP layouts
Week 3   │ Phase 3 P0–P1 pages (both codebases in parallel)
Week 4   │ Phase 3 P2 pages
Week 5   │ Phase 3 P3 + Phase 4 intake / wizards
Week 6   │ Phase 4 remaining pages
Week 7   │ Phase 5 cleanup + visual QA
Week 8   │ Buffer / stakeholder fixes
```

**Parallel staffing (recommended):**

| Developer | Focus |
|-----------|-------|
| Dev A | Design system package, LOS layouts + pages |
| Dev B | PLP shared package, 3 portals + pages |

With 2 developers: **~4–5 weeks** to Phase 5.

---

## 8. Quality assurance

### 8.1 Smoke test matrix (after each phase)

| App | Path | Steps |
|-----|------|-------|
| LOS staff | `/los/login` → dashboard → applications | Login, list load, open one application |
| LOS borrower | `/los/borrower/login` → dashboard | Login, dashboard KPIs |
| PLP admin | `/plp/login` → programs | Login, list programs |
| PLP borrower | `/plp-borrower/login` → loans | Login, my loans |
| PLP anchor | `/plp-anchor/login` → dashboard | Login, dashboard |
| Payment-Terms | `/login` → dashboard | Login, dashboard |

### 8.2 Regression

- Re-run existing manual / E2E scripts — **expect zero flow changes**
- Compare notification, auth, and API payloads — unchanged

### 8.3 Style audit (Phase 5)

Grep for deprecated patterns; target zero matches in `src/`:

```
bl-primary|#1890ff|#2563eb|bg-sky-|bg-emerald-|brand-mid
```

Exception: semantic status colors inside `BtBadge` mapping only.

### 8.4 Accessibility

- Focus rings visible (orange)
- Badge text contrast ≥ WCAG AA on status colors
- Form labels associated with inputs (unchanged behavior)

---

## 9. Risk register

| ID | Risk | Likelihood | Impact | Mitigation |
|----|------|------------|--------|------------|
| R1 | White sidebar rejected vs current navy | Medium | Medium | Week-1 LOS spike; stakeholder sign-off before PLP |
| R2 | Tailwind v3 vs v4 incompatibility | Medium | Low | Copy raw CSS components; avoid `@apply` cross-version |
| R3 | Scope creep into flow changes | Medium | High | PR review checklist: CSS/className only |
| R4 | Missing logo assets in repo | Low | Medium | Add `billiontech-logo.png` + BillionLoans PNGs to all `public/` |
| R5 | Big-bang page migration breaks UI | Medium | High | Phased pages + token shims |
| R6 | PLP triplicated layouts drift again | Medium | Medium | Shared `AppShell` component in `@plp/shared` |

---

## 10. Decision log

| ID | Decision | Options considered | Chosen | Date |
|----|----------|-------------------|--------|------|
| D1 | Primary brand color | Orange (BT) vs keep blue for lending | **Orange everywhere** | TBD |
| D2 | Sidebar treatment | White vs keep dark navy | **White sidebar + orange active** (pending spike review) | TBD |
| D3 | Portal accent colors | Keep sky/emerald vs unified | **Remove; subtitle only** | TBD |
| D4 | Typography | IBM Plex vs Inter + Jakarta | **Inter + Plus Jakarta Sans** | TBD |
| D5 | Package location | Monorepo folder vs npm | **Monorepo folder → npm later** | TBD |
| D6 | Payment-Terms layout | Copy to LOS/PLP vs adapt | **Adapt tokens only** | Approved |

---

## 11. Roles and responsibilities

| Role | Responsibility |
|------|----------------|
| Product owner | Approve visual direction; sign off Phase 2 spike |
| Dev A | Design system extraction, LOS migration |
| Dev B | PLP shared package, three portal migrations |
| QA | Smoke matrix after each phase |
| DevOps | Ensure brand assets deployed to all environments |

---

## 12. Proof-of-concept sprint (recommended start)

**Duration:** 5 working days  
**Scope:** Phase 0 + LOS `MainLayout` + staff login + dashboard

| Day | Deliverable |
|-----|-------------|
| 1 | Extract `design-system/` from Payment-Terms |
| 2 | Wire into LOS; verify `bl-*` aliases |
| 3 | Reskin `MainLayout` + `BrandedAuthFrame` |
| 4 | Reskin staff dashboard + applications list (P1 sample) |
| 5 | Demo + go/no-go for full rollout |

**Go criteria:**

- [ ] Orange primary acceptable on dense ops UI
- [ ] White sidebar readability approved
- [ ] Zero login / navigation regressions

**No-go fallback:** Keep navy sidebar; apply orange primary + BT components only (partial unification).

---

## 13. Deliverables summary

| # | Deliverable | Phase |
|---|-------------|-------|
| 1 | `billiontech-design-system/` token package | 0 |
| 2 | Shared UI primitives (`BrandLogo`, `BtButton`, …) | 1 |
| 3 | Reskinned layouts (6 shells) | 2 |
| 4 | P0–P2 pages migrated | 3 |
| 5 | Full page sweep + polish | 4 |
| 6 | `DESIGN_SYSTEM.md` + visual baseline | 5 |
| 7 | Deprecated token removal | 5 |

---

## 14. Appendix A — File inventory (migration targets)

### LOS (`D:\LOS\los-app\los-app\ui-service`)

| Category | Key files |
|----------|-----------|
| Global CSS | `src/index.css` |
| Layouts | `src/layouts/MainLayout.tsx`, `BorrowerPortalLayout.tsx`, `BorrowerLayout.tsx` |
| Branding | `src/components/BrandLogo.tsx`, `BrandedAuthFrame.tsx` |
| Intake | `src/lib/intake/intakeStepLayout.ts` |

### PLP (`D:\PLP\PLP-APP\frontend`)

| Category | Key files |
|----------|-----------|
| Global CSS | `packages/*/src/index.css` (×3) |
| Layouts | `platform-ui/.../MainLayout.tsx`, `borrower-portal/.../BorrowerLayout.tsx`, `anchor-portal/.../AnchorLayout.tsx` |
| Branding | `packages/shared/src/components/portalBranding.tsx` |
| Login | `platform-ui/.../LoginPage.tsx`, borrower/anchor login pages |

### Payment-Terms (`C:\Users\User\git\payment-terms-engine\frontend`)

| Category | Key files |
|----------|-----------|
| Design system source | `src/index.css`, `tailwind.config.js` |
| Layout reference | `src/components/common/Layout.js` |
| Auth reference | `src/pages/Login.js` |

---

## 15. Appendix B — Approval

| Role | Name | Signature | Date |
|------|------|-----------|------|
| Product owner | | | |
| Engineering lead | | | |
| Design / brand | | | |

---

*End of document*
