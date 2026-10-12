# Employee Portal (Next.js)

Frontend of the Corporate Travel & Expense platform. Next.js 16.4 (App Router), React 19.3, next-auth 5 (Keycloak, PKCE), Tailwind 4, shadcn/ui.

## Run

```bash
cp .env.example .env.local     # set AUTH_SECRET / NEXTAUTH_SECRET
npm ci
npm run dev                    # http://localhost:3000
```

Needs the backend from the repository root (`podman compose up -d`). Node 22+.

## How it talks to the backend

`next.config.ts` rewrites, so the browser only ever calls `localhost:3000`:

| Path | Goes to | Used for |
|---|---|---|
| `/api/auth/*` | next-auth | Keycloak login, refresh, logout |
| `/api/bff/*` | Employee BFF (8085) | dashboard, bookings, expenses, delegation mode, delegation grants |
| `/api/{bookings,expenses,delegations,consents}/*` | API Gateway (8000) | direct service calls |

The BFF keeps delegation state in its session cookie; delegation tokens never reach the browser.

## Checks

```bash
npm run lint        # ESLint 9 + eslint-config-next (React Compiler rules)
npx tsc --noEmit
npm run build
```

`proxy.ts` (Next 16's replacement for `middleware.ts`) guards the app routes and sends expired sessions to `/session-expired`.
