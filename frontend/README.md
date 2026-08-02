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
