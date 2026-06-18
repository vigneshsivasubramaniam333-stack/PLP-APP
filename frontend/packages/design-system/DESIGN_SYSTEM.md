# BillionTech Design System

Unified visual language for LOS, PLP (admin / borrower / anchor), and Payment-Terms.

**Canonical source:** `frontend/packages/design-system/` (extracted from Payment-Terms `index.css`).

## Tokens

| Token | Value | Usage |
|-------|-------|-------|
| `--bt-orange` | `#F97316` | Primary actions, active nav, brand accent |
| `--bt-orange-light` | `#FFF7ED` | Active sidebar tint, icon backgrounds |
| `--bt-gray-50` | `#F8F9FB` | App canvas / page background |
| `--bt-gray-900` | `#111827` | Headings, primary text |
| `--bt-green` | success states | Badges, alerts |
| `--bt-red` | errors, overdue | Badges, alerts |

Import in any app:

```css
@import '../../design-system/index.css';
@import '../../design-system/tailwind-v4-bridge.css'; /* Tailwind v4 apps only */
```

### Legacy shims (temporary)

During migration, legacy aliases map to BT tokens:

- LOS: `--color-bl-primary` → `--bt-orange` (via `@theme` bridge)
- PLP: `--brand-accent` → `--bt-orange`

Remove shims once all `bl-*` / inline legacy colors are migrated.

## Components

Use CSS classes or React wrappers from `@plp/shared`:

| Class / Component | Purpose |
|-------------------|---------|
| `.bt-btn` / `BtButton` | Primary, secondary, danger buttons |
| `.bt-input` / `BtInput` | Text inputs |
| `.bt-card` / `BtCard` | Content panels |
| `.bt-badge` / `BtBadge` | Status chips — use `status` prop or `badgeToneForStatus()` |
| `.bt-table` | Data tables |
| `.bt-page-header` / `BtPageHeader` | Page title + actions |
| `.bt-sidebar-wide` | White sidebar shell (LOS / PLP wide layouts) |
| `.bt-sidebar-link.active` | Orange active nav item |
| `BrandedAuthFrame` | Split login (form + orange gradient hero) |

Preview all variants at **Platform Admin → `/design-preview`** (dev/QA).

## Layout pattern

```
┌──────────────────────┐
│ BillionLoans logo    │
│ Portal subtitle      │
├──────────────────────┤
│ [active] Dashboard   │  ← bg #FFF7ED, text #F97316
│ Applications         │
└──────────────────────┘
```

**Do not** copy Payment-Terms’ 52px icon rail into LOS/PLP — same skin, different skeleton.

## Do / Don't

**Do**

- Use `BtBadge` / `badgeToneForStatus` for status colors
- Use `bt-btn-primary` for all primary CTAs
- Keep routes, APIs, auth, and nav structure unchanged during reskin

**Don't**

- Hard-code `#1890ff`, `#2563eb`, `bg-sky-*`, or `bg-emerald-*` for branding (semantic success via `bt-badge-green` is OK)
- Mix navy sidebars with BT white sidebar pattern
- Add business logic to presentational `@plp/shared` UI components

## Visual regression baseline

Capture screenshots after each release for:

| App | Pages |
|-----|-------|
| LOS staff | login, dashboard, applications list |
| LOS borrower | login, dashboard |
| PLP admin | login, dashboard, programs |
| PLP borrower | login, dashboard |
| PLP anchor | login, dashboard |

## Package layout

```
frontend/packages/design-system/
  tokens.css          — :root --bt-* variables
  components.css      — .bt-btn, .bt-card, .bt-input, …
  layouts.css         — sidebar, auth split
  fonts.css           — Inter + Plus Jakarta Sans
  tailwind-v4-bridge.css — @theme mappings for v4 apps
  portals-base.css    — PLP portal :root shims
  index.css           — main entry
```

LOS mirrors design-system under `ui-service/src/design-system/` (sync from PLP package when tokens change).
