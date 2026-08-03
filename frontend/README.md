# frontend

React SPA — the single UI surface a taxpayer interacts with. Talks to all 4 backend services
through Vite's dev-server proxy (see [`vite.config.ts`](vite.config.ts)); there's no gateway
in front of it yet — see the [root README](../README.md#architecture) for the real routing.

## Stack

React 18 + Vite + TypeScript, React Hook Form + Zod for forms/validation, Tailwind, Radix UI.

## Pages

| Page | Route area | Purpose |
|---|---|---|
| `LoginPage` | auth-service | Login |
| `RegisterPage` | auth-service | Registration |
| `DashboardPage` | taxpayer-service | List/create taxpayers (paginated) |
| `TaxpayerDetailPage` | refund-service, ai-service | Filings, refund status, prediction + guidance |

## Run

```bash
npm install
npm run dev
```

Requires all 4 backend services running (or at least the ones the page you're on needs — see
`vite.config.ts` for which service owns which path prefix). Opens on http://localhost:5173.

## Build

```bash
npm run build      # tsc -b && vite build
npm run preview    # serve the production build locally
```

## Test

```bash
npm run test:e2e      # Playwright, headless
npm run test:e2e:ui   # Playwright, interactive UI mode
```

E2E tests expect the full stack (all 4 services + infra) up via `docker compose up -d` and
each service running, plus the dev server itself.

## Key terms

- **Vite dev-server proxy** — rewrites `/api/v1/*` requests to the right backend service by
  path prefix during local dev (see `vite.config.ts`). This is the actual routing mechanism
  today — there's no real API gateway in front of the app yet.
- **React Hook Form + Zod** — form state is managed by React Hook Form; Zod schemas declare
  the validation rules once and get enforced on submit, instead of hand-rolled field checks.
- **Radix UI** — unstyled, accessible component primitives (dialogs, dropdowns, etc.) that
  this app's own styling is layered on top of, rather than a themed component library.
- **Playwright** — the E2E test framework. It drives the real rendered app in a real browser
  against the real backend services, not a mocked API layer.
