# Next.js Frontend — Corporate Travel Portal: Phased Implementation Plan

## Context

The backend for this corporate travel portal is fully built (Spring Boot microservices, 71/71 E2E tests passing). This plan covers building the React frontend from scratch, translating 13 static HTML mockups (at `frontend/mockups/`) into a production-grade Next.js 14 application with real API integration, Keycloak OIDC authentication, and a complex delegation/identity flow (RFC 8693 Token Exchange V2).

The frontend must surface a sophisticated identity model where an assistant (Dave) can act on behalf of an executive (Carol) via delegation + consent + token exchange — with the UI visually distinguishing delegated actions via amber banners and row tints.

---

## Tech Stack

| Concern | Choice |
|---|---|
| Framework | Next.js 14+ (App Router) |
| Language | TypeScript |
| Styling | Tailwind CSS v3 |
| Design system | shadcn/ui (Slate base, CSS variables ON) |
| Auth | next-auth v5 (Keycloak OIDC provider) |
| HTTP | Axios with interceptors |
| Forms | react-hook-form + zod |
| Font | Inter (via `next/font/google`) |

---

## Project Directory Layout

```
corporate-travel-portal/
└── frontend/
    └── nextjs/
        ├── app/
        │   ├── layout.tsx                   ← root layout, providers
        │   ├── page.tsx                     ← redirect to /dashboard or /login
        │   ├── api/
        │   │   ├── auth/[...nextauth]/route.ts
        │   │   └── health/route.ts          ← server-side health fanout (Phase 7)
        │   ├── (auth)/
        │   │   └── login/page.tsx
        │   └── (app)/                       ← protected shell layout
        │       ├── layout.tsx               ← AppShell: TopNav + Sidebar + DelegationBanner
        │       ├── dashboard/page.tsx       ← role-splits employee/manager/admin
        │       ├── travel/
        │       │   ├── page.tsx             ← booking list
        │       │   ├── book/page.tsx
        │       │   └── [id]/page.tsx
        │       ├── expense/
        │       │   ├── page.tsx             ← expense list
        │       │   ├── submit/page.tsx
        │       │   └── [id]/page.tsx
        │       ├── delegation/page.tsx
        │       └── admin/audit/page.tsx     ← Phase 7
        ├── components/
        │   ├── layout/
        │   │   ├── TopNav.tsx
        │   │   ├── Sidebar.tsx
        │   │   └── DelegationBanner.tsx
        │   ├── ui/                          ← shadcn/ui installed components
        │   ├── shared/
        │   │   ├── StatusBadge.tsx
        │   │   ├── StatCard.tsx
        │   │   ├── AuditTrail.tsx
        │   │   ├── IdentityContextPanel.tsx
        │   │   └── Pagination.tsx
        │   ├── travel/
        │   │   ├── BookingTable.tsx
        │   │   ├── BookingForm.tsx
        │   │   └── BookingDetail.tsx
        │   ├── expense/
        │   │   ├── ExpenseTable.tsx
        │   │   ├── ExpenseItemEditor.tsx    ← useFieldArray dynamic rows
        │   │   └── ExpenseDetail.tsx
        │   └── delegation/
        │       ├── DelegationTable.tsx
        │       ├── ConsentTable.tsx
        │       └── GrantDelegationModal.tsx
        ├── lib/
        │   ├── api/
        │   │   ├── client.ts               ← Axios: attaches Bearer, handles 401/403
        │   │   ├── bff.ts                  ← /api/bff/* typed wrappers (port 8085)
        │   │   ├── gateway.ts              ← direct service calls (port 8000)
        │   │   ├── delegation.ts           ← /api/delegations/* (gateway)
        │   │   └── consent.ts              ← /api/consents/* (gateway)
        │   ├── auth/auth.ts                ← next-auth config
        │   ├── hooks/
        │   │   ├── useDelegationContext.ts
        │   │   ├── useBookings.ts
        │   │   ├── useExpenses.ts
        │   │   └── useDelegations.ts
        │   └── types/
        │       ├── booking.ts
        │       ├── expense.ts
        │       ├── delegation.ts
        │       └── auth.ts
        ├── middleware.ts                    ← protects (app)/* routes
        ├── next.config.ts
        ├── tailwind.config.ts
        ├── components.json                 ← shadcn config
        └── .env.local
```

---

## Critical Architecture Notes (Read Before Each Session)

1. **BFF-first**: The frontend ONLY talks to Employee BFF (`localhost:8085`) for bookings and expenses. Never call travel-service or expense-service directly.
2. **Two sessions**: NextAuth manages the browser JWT session; the BFF maintains a separate server-side Spring `DelegationContext` session. Call `GET /api/bff/delegation/context` on mount — do NOT read delegation state from NextAuth session.
3. **withCredentials**: All Axios calls must use `withCredentials: true` so the Spring session cookie is sent.
4. **Direct gateway calls**: Expense approve/reject, delegation CRUD, and consent CRUD go to API Gateway (`localhost:8000`). The BFF does not proxy these.
5. **Delegation banner shifts layout**: When `delegationActive` is true, sidebar top shifts from `top-14` to `top-24` and main content `pt-14` → `pt-24`.
6. **Amber tint on delegated rows**: Any table row where `delegationId !== null` gets `bg-amber-50/10 hover:bg-amber-50/30`.
7. **Role-based UI, never username-based**: All conditional rendering (manager approve buttons, delegation grant, assistant activation, admin dashboard) must derive from `session.user.roles` (`realm_access.roles` from JWT). Never branch on username. The same user can change roles; a new user can have any role.
8. **Booking source is fixed**: The departure origin for all bookings is always "Office HQ — Mumbai, India". It is a display-only field, not editable by the user.

---

## API Quick Reference

| Action | Method | Endpoint |
|---|---|---|
| Active delegation context | GET | `BFF /api/bff/delegation/context` |
| Dashboard data | GET | `BFF /api/bff/dashboard` |
| List bookings | GET | `BFF /api/bff/bookings` |
| Create booking | POST | `BFF /api/bff/bookings` |
| Get booking | GET | `BFF /api/bff/bookings/{id}` |
| Booking audit | GET | `GW /api/bookings/{id}/audit` |
| List expenses | GET | `BFF /api/bff/expenses` |
| Create expense | POST | `BFF /api/bff/expenses` |
| Submit expense | POST | `GW /api/expenses/{id}/submit` |
| Approve expense | POST | `GW /api/expenses/{id}/approve` |
| Reject expense | POST | `GW /api/expenses/{id}/reject` |
| Expense audit | GET | `GW /api/expenses/{id}/audit` |
| Activate delegation | POST | `BFF /api/bff/delegation/activate/{id}?audience=travel-service` |
| Deactivate delegation | DELETE | `BFF /api/bff/delegation/deactivate` |
| My delegations (granted by me) | GET | `GW /api/delegations/my-delegations` |
| Delegations to me | GET | `GW /api/delegations/to-me` |
| Create delegation | POST | `GW /api/delegations` |
| Revoke delegation | DELETE | `GW /api/delegations/{id}` |
| My consents | GET | `GW /api/consents/my-consents` |

---

## Test Users

| Username | Password | Role | Tenant |
|---|---|---|---|
| alice.employee | password123 | employee | tenant-a |
| bob.manager | password123 | manager | tenant-a |
| carol.executive | password123 | executive | tenant-a |
| dave.assistant | password123 | assistant | tenant-a |
| eve.employee | password123 | employee | tenant-b |

Keycloak token endpoint: `POST http://localhost:8080/realms/corporate-travel/protocol/openid-connect/token`

---

## Phase 0 — Dummy Data Seeding

### Goal
Seed the backend with realistic travel data across 9 geographies so all booking and expense screens have meaningful content to display from Phase 3 onwards.

### Destinations (one per geography)

| Geography | City | Destination Label |
|---|---|---|
| India | Bengaluru | Bengaluru, India |
| China | Shanghai | Shanghai, China |
| Japan | Tokyo | Tokyo, Japan |
| Australia | Sydney | Sydney, Australia |
| United Kingdom | London | London, UK |
| Germany | Berlin | Berlin, Germany |
| France | Paris | Paris, France |
| UAE | Dubai | Dubai, UAE |
| USA | New York | New York, USA |

### What to seed

For each of the 5 test users, create a varied mix of bookings and expenses that covers all booking types (FLIGHT, HOTEL, CAR), all booking statuses (DRAFT, CONFIRMED, COMPLETED, CANCELLED), all expense statuses (DRAFT, SUBMITTED, APPROVED, REJECTED, PAID), and at least 2 delegated bookings/expenses (actorId=dave.assistant, subjectId=carol.executive).

Suggested seed breakdown per user:

- **alice.employee**: 3 bookings (confirmed London, completed Tokyo, draft Paris), 2 expenses (approved+paid, draft)
- **bob.manager**: 2 bookings (confirmed NYC, completed Sydney), 1 expense (submitted, pending approval)
- **carol.executive**: 3 bookings (2 direct, 1 created-by-dave via delegation), 2 expenses (1 direct, 1 via delegation)
- **dave.assistant**: 1 own booking, 2 bookings as delegate for carol
- **eve.employee**: 2 bookings (different tenant — tenant-b; should never appear in tenant-a views)

### How to seed

Write a shell script `scripts/seed-data.sh` that:
- Obtains a JWT for each user via Keycloak password grant
- Uses `curl` calls against the Employee BFF (`localhost:8085`) for bookings/expenses
- Uses `curl` against the API Gateway (`localhost:8000`) for delegation and consent records
- Activates delegation before creating delegated bookings, deactivates after

### Seeding notes

- Source for all bookings: "Office HQ — Mumbai, India" (hardcoded, not a stored field)
- Amounts should be realistic: flights INR 45,000–120,000, hotels INR 8,000–20,000/night, car INR 2,000–5,000/day
- Expense items should reference realistic categories: TRAVEL (flight), ACCOMMODATION (hotel), MEALS, TRANSPORTATION (car/taxi)

### Verification

- Log in as `alice.employee` → booking list shows 3 bookings across 3 destinations
- Log in as `bob.manager` → Pending Approvals dashboard has at least 1 submitted expense
- Log in as `dave.assistant` → "Granted to Me" shows Carol's delegation; activate it → booking list shows Carol's bookings

---

## Phase 1 — Project Scaffolding, Design System, and App Shell

### Goal
A running Next.js app with the full visual chrome — top nav, dark sidebar, delegation banner placeholder — that looks identical to the mockups with no live data and no auth enforcement.

### Steps

**1. Scaffold** the Next.js app in `frontend/nextjs/`:
```bash
npx create-next-app@latest nextjs --typescript --tailwind --app --import-alias "@/*"
cd nextjs
npx shadcn@latest init   # Slate base, CSS variables ON
npx shadcn@latest add button badge avatar separator tooltip
npm install next-auth@beta @auth/core axios clsx react-hook-form zod @hookform/resolvers
```

**2. `tailwind.config.ts`**: Add `fontFamily: { sans: ['Inter', ...] }`. No custom color tokens needed — use Tailwind's built-in slate, blue, emerald, amber, sky, violet, red palettes.

**3. `(app)/layout.tsx`** (AppShell): Renders `<TopNav>` (h-14, fixed top, white), conditional `<DelegationBanner>` (h-10, amber-50, fixed below nav), `<Sidebar>` (w-64, fixed left, top varies), and `<main>` (ml-64, pt varies). Use a `DelegationContextProvider` React context to share banner visibility state.

**4. `TopNav.tsx`**: Left: TravelCorp logo (blue-600 rounded icon + text). Right: role badge derived from `session.user.roles` — priority order manager=violet, admin=purple, executive=emerald, assistant=amber, employee=blue — plus user name and avatar initials (same color as role badge). Never key off username.

**5. `Sidebar.tsx`**: bg-slate-900. Nav links with active=`bg-slate-800 text-white`, inactive=`text-slate-400 hover:bg-slate-800 hover:text-white`. Role-conditional sections derived from `session.user.roles`:
- All authenticated users: Dashboard, My Trips, My Expenses, Delegations
- `roles.includes('manager')`: adds Team section with Pending Approvals (red count badge)
- `roles.includes('admin')`: adds Administration section (Audit Log, System Health)
- Sign out at bottom (border-t border-slate-800)

**6. `DelegationBanner.tsx`**: amber-50 bg, amber-200 border-b. Shows `{actorName} is acting on behalf of {subjectName}` + mono IDs + purpose. Exit button calls deactivate API. Add `role="alert"` for accessibility.

**7. `StatusBadge.tsx`**: Single component mapping status → color pair:
- Confirmed, Approved, Active → emerald
- Submitted, Pending → amber
- Draft, Completed, Expired → slate
- Rejected, Cancelled → red

**8. `StatCard.tsx`**: White card, border-slate-200, rounded-xl, p-5. Props: `label`, `value`, `subtitle`, `icon`, `iconBgClass`.

### shadcn/ui components
`Button`, `Badge`, `Avatar`, `Separator`, `Tooltip`

### Verification
- `npm run dev` → `localhost:3000` shows full chrome with placeholder data
- Set `MOCK_DELEGATION_ACTIVE = true` → amber banner appears, sidebar shifts to top-24
- No Tailwind errors, Inter font loads, no console errors

---

## Phase 2 — Authentication (Keycloak OIDC via NextAuth)

### Goal
Real login via Keycloak. JWT stored in NextAuth session. Every API call to BFF sends `Authorization: Bearer {access_token}`. Protected routes redirect to login.

### Steps

**1. `lib/auth/auth.ts`** — NextAuth v5 Keycloak provider:
```typescript
// issuer: process.env.KEYCLOAK_ISSUER
// clientId: process.env.KEYCLOAK_CLIENT_ID
// callbacks.jwt: store access_token, refresh_token
// callbacks.session: expose accessToken, user.roles (from realm_access.roles),
//                    user.tenantId, user.name, user.email
```

**2. `app/(auth)/login/page.tsx`**: Matches `auth/login.html`. Centered TravelCorp logo + "Continue with Corporate SSO" button calling `signIn('keycloak')`. Remove the "Quick access" mockup panel — that was prototype-only.

**3. `middleware.ts`**: Protect all `/(app)/*` routes — redirect to `/login` if no session.

**4. `lib/api/client.ts`** — Axios instance:
- `baseURL`: `process.env.NEXT_PUBLIC_BFF_URL` (`:8085`)
- `withCredentials: true` (critical for Spring session cookie)
- Request interceptor: attach `Authorization: Bearer {accessToken}`
- Response interceptor: 401 → `signOut()`, 403 → show toast "Not authorized"

**5. `lib/hooks/useDelegationContext.ts`**:
- Calls `GET /api/bff/delegation/context` on mount
- Stores in React context (`DelegationContextProvider`)
- Exposes: `{ delegationActive, actorId, subjectId, delegationId, consentId, expiresAt, exitDelegation() }`
- `exitDelegation()` calls `DELETE /api/bff/delegation/deactivate` then refreshes

**6. `.env.local`**:
```
NEXTAUTH_URL=http://localhost:3000
NEXTAUTH_SECRET=<random-string>
KEYCLOAK_CLIENT_ID=employee-portal
KEYCLOAK_CLIENT_SECRET=<from-keycloak-admin>
KEYCLOAK_ISSUER=http://localhost:8080/realms/corporate-travel
NEXT_PUBLIC_BFF_URL=http://localhost:8085
NEXT_PUBLIC_GATEWAY_URL=http://localhost:8000
```

### shadcn/ui components
`Toast` / `Sonner` (for 403 feedback)

### Verification
- `/dashboard` unauthenticated → redirects to `/login`
- Login as `carol.executive` → TopNav shows name, role badge "Employee" (executive role)
- Login as `bob.manager` → sidebar shows "Team" section with Pending Approvals
- Network tab: BFF requests include `Authorization: Bearer ...`
- 403 from BFF → toast appears, no crash

---

## Phase 3 — Dashboard Pages (All Roles)

### Goal
Three role-specific dashboard views pulling live data from `GET /api/bff/dashboard`.

### Steps

**1. `app/(app)/dashboard/page.tsx`**: Read `session.user.roles` (from `realm_access.roles` in JWT — never from username). Render `<AdminDashboard>` if roles includes `admin`, `<ManagerDashboard>` if roles includes `manager`, otherwise `<EmployeeDashboard>`. Priority order: admin > manager > employee/executive/assistant.

**2. Employee Dashboard** (from `dashboard/employee.html`):
- Page header: "Good morning, {firstName}" + date
- CTA buttons: "New Expense" → `/expense/submit`, "Book Trip" → `/travel/book`
- 4 stat cards: Upcoming Trips (sky icon), Open Expenses (amber icon), Spent This Month (emerald icon), Delegations (amber icon)
- Recent Trips table: Booking ID (link), Destination, Dates, Type badge, Amount, Status badge
- Recent Expenses table: Report ID (link), Description, Submitted, Amount, Status badge
- If delegation active: amber info card "Dave Wilson can act on your behalf"

**3. Manager Dashboard** (from `dashboard/manager.html`):
- Stat cards: Pending Approvals (red), Team Trips, Team Spend, Active Delegations
- Pending Approvals table: Report ID, Employee (avatar + name + optional "delegate" badge if delegated row), Description, Amount, Submitted, Approve/Reject buttons
- Delegated rows: amber-50/10 tint
- Active Delegations table: Subject, Actor, Purpose (amber mono badge), Scopes, Expires, Status

**4. Admin Dashboard** (from `dashboard/admin.html`):
- Stat cards: Active Users, Active Delegations, OPA Decisions 24h, Policy Violations 24h
- Service Health list: service name, port, status dot (green=UP/red=DOWN), latency — static placeholder in this phase, wired to live data in Phase 7
- Recent Audit Events table: Time, Action, Actor (blue), Subject (amber), Resource, Result badge. DENY rows get red-50 background.

**5. TypeScript interfaces**: `lib/types/booking.ts`, `lib/types/expense.ts` matching all API response shapes.

### shadcn/ui components
`Table`, `Card`, `Skeleton` (loading states), `DropdownMenu`

### Verification
- Employee role user → stat counts match live BFF data
- Manager role user → Pending Approvals table shows real rows; clicking Approve → status updates
- Admin role user → dashboard renders; service health shows static placeholder
- Delegation notice card appears on employee dashboard when delegation is active

---

## Phase 4 — Travel Bookings (List, Create, Detail)

### Goal
Full CRUD for bookings with delegation-aware UI.

### Steps

**1. `app/(app)/travel/page.tsx`** (from `travel/list.html`):
- When delegation active: subtitle "Showing bookings for {subjectName}" + "Book for {subjectName}" CTA button
- Filter bar: Type select (FLIGHT/HOTEL/CAR), Status select, date range inputs, Filter/Reset
- Table columns: Booking ID (link), Destination, Type badge, Dates, Amount, Booked By (amber "delegate" badge if `createdBy !== userId`), Status badge, View link
- Delegated rows: `bg-amber-50/10 hover:bg-amber-50/30`
- Pagination: "Showing N of M" + page number buttons

**2. `app/(app)/travel/book/page.tsx`** (from `travel/book.html`):
- Booking type: 3-column radio card grid (Flight/Hotel/Car) using `RadioGroup` with custom `peer-checked` styled cards
- Fields:
  - **Source** (read-only): "Office HQ — Mumbai, India" — non-editable info field with lock icon
  - **Destination** (required): text input with suggested destinations from Phase 0 seed data
  - Departure Date (required), Return Date (required, must be ≥ departure)
  - Business Purpose (required), Estimated Amount (number), Currency select (INR/USD/EUR/SGD), Notes (textarea)
- Identity context panel (slate-50 card): shows userId/subject, createdBy/actor, delegationId, tenantId — values from session + `useDelegationContext()`
- On success: `POST /api/bff/bookings` → redirect to new booking's detail page

**3. `app/(app)/travel/[id]/page.tsx`** (from `travel/detail.html`):
- Header: destination as title, status badge, booking ID in monospace, "Submit Expense" button linking to `/expense/submit?bookingId=...`
- Booking Details card: key-value rows for type, destination, travel dates, amount, created timestamp
- Identity & Audit Trail card (amber-50 bg, only if `delegationId !== null`): userId/subject (amber text), createdBy/actor (blue text), delegationId, consentId, tenantId, purpose. Footer: "Headers forwarded: X-Delegated-Subject · X-Delegation-Id · X-Actor-Token"
- Event Timeline: `<AuditTrail>` component with colored timeline dots

**4. `components/shared/AuditTrail.tsx`**: Vertical timeline component. Props: `events: Array<{ label, timestamp, actor, subject, color }>`.

**5. `components/shared/IdentityContextPanel.tsx`**: Key-value panel. Renders with amber border when `delegationId` present; slate border otherwise.

**6. `lib/api/bff.ts`** additions: `getBookings(filters)`, `createBooking(body)`, `getBooking(id)`.

### shadcn/ui components
`Select`, `Input`, `Textarea`, `RadioGroup`, `Breadcrumb`

### Verification
- Booking list loads from BFF; type and status filters send query params
- Create form: client-side validation (destination required, return ≥ departure); source field is locked
- Delegation booking detail: amber Identity panel appears; non-delegation booking: panel hidden
- When delegation active, CTA reads "Book for {subjectName}"

---

## Phase 5 — Expense Management (List, Submit, Approve)

### Goal
Complete expense flow: list, multi-item submission form, detail with approval chain, manager approve/reject.

### Steps

**1. `app/(app)/expense/page.tsx`** (from `expense/list.html`):
- Summary strip: 4 mini stat cards (Total Submitted, Approved & Paid, Pending Approval, Draft)
- Status filter: segmented toggle (All / Draft / Submitted / Approved / Rejected)
- Table columns: Report ID (link), Title (amber "via delegate" badge if `delegationId !== null`), Trip link (mono), Items count, Total, Submitted date, Status badge, Actions
- Delegated rows: amber-50/10 tint
- Footer note: "N report(s) submitted via delegation"

**2. `app/(app)/expense/submit/page.tsx`** (from `expense/submit.html`):
- When delegation active: subtitle "This report will be attributed to {subjectName}"
- Report Details card: Title (required), Description (textarea), Linked Trip select (from `GET /api/bff/bookings`), Currency select
- Expense Items section — dynamic `useFieldArray` rows: Category select (TRAVEL/ACCOMMODATION/MEALS/TRANSPORTATION/OTHER), Description input, Date input, Amount input, Delete button. Running total in section footer.
- Identity context panel (amber-50 bg when delegation active, slate-50 otherwise)
- "Save as Draft" → `POST /api/bff/expenses` with status DRAFT
- "Submit for Approval" → create expense then `POST /api/expenses/{id}/submit` via gateway

**3. `app/(app)/expense/[id]/page.tsx`** (from `expense/detail.html`):
- Header: title, status badge, optional "via delegate" amber badge, expense ID monospace
- Report Details card + Expense Items table with total footer row
- Approval Chain card: approver avatar + name + status. Approve and Reject buttons rendered only when `session.user.roles.includes('manager')` — calls `POST /api/expenses/{id}/approve|reject` via gateway. OPA enforces this server-side too; client-side is a UX courtesy only.
- Identity & Audit Trail card (if `delegationId !== null`)
- Event Timeline: `<AuditTrail>` component

**4. `lib/api/bff.ts`** additions: `getExpenses(filters)`, `createExpense(body)`, `getExpense(id)`.

**5. `lib/api/gateway.ts`**: New Axios instance pointing to `localhost:8000`. Used for expense approve/reject/submit and later delegation/consent CRUD. Same interceptors as BFF client.

### shadcn/ui components
`Form` (react-hook-form integration), `Label`, `Tabs`

### Verification
- Expense list loads with all statuses; filter toggle works
- Submit form: add 3 items, running total updates; Save as Draft creates DRAFT visible in list; Submit transitions to SUBMITTED
- Manager role on detail: Approve button appears and calls gateway correctly; non-manager: button hidden
- Delegated expense detail: amber identity panel shows; non-delegated: hidden

---

## Phase 6 — Delegation and Consent Management

### Goal
The delegation management page with end-to-end token exchange activation flow wired up.

### Steps

**1. `app/(app)/delegation/page.tsx`** (from `delegation/manage.html`):
- Blue info box explaining delegation (actorId / subjectId / delegationId concepts)
- "Granted by Me" table: ID (mono), Delegate name, Purpose (amber mono badge), Scopes (slate chips), Granted date, Expires (amber text if near expiry), Status badge, Revoke button. Expired rows at 50% opacity.
- "Granted to Me" table: same columns minus Revoke button. Adds "Activate" button for ACTIVE status rows.
- "Consent Records" table: Consent ID (blue mono), Linked Delegation (mono), Purpose (amber badge), Granted By, Valid Until (amber if active), Status badge.

**2. `components/delegation/GrantDelegationModal.tsx`** (from `delegation/manage.html` modal):
- Dialog with Delegate select, Purpose select (`book_travel` / `approve_expenses` / `view_reports`), Scopes checkboxes (`view_bookings` / `create_bookings` / `view_expenses` / `create_expenses`), Expiry datetime-local input
- Amber summary preview box: "This will create: a delegation record + a consent record"
- Submit → `POST /api/delegations` via gateway

**3. Activation flow** (critical path):
- "Activate" button → `POST /api/bff/delegation/activate/{delegationId}?audience=travel-service`
- On success: `useDelegationContext` refreshes → `delegationActive = true` → amber banner appears without page reload
- "Exit" in banner → `DELETE /api/bff/delegation/deactivate` → banner disappears, layout shifts back

**4. `lib/api/delegation.ts`**: `getMyDelegations()`, `getDelegationsToMe()`, `createDelegation(body)`, `revokeDelegation(id)`.

**5. `lib/api/consent.ts`**: `getMyConsents()`.

### shadcn/ui components
`Dialog`, `Checkbox`, `AlertDialog` (revoke confirmation)

### Verification
- Executive role user: "Granted by Me" shows existing delegation; Revoke works; Grant modal creates new delegation
- Assistant role user: "Granted to Me" shows delegations; clicking "Activate" → amber banner appears immediately
- Banner shows delegation ID, purpose, consent ID, expiry correctly
- Exit → banner disappears, sidebar shifts back to normal top position
- Travel list while delegation active: subtitle shows delegatee's name

---

## Phase 7 — Audit Trails and Admin Dashboard

### Goal
Live audit trail data on detail pages; admin dashboard with real service health polling; paginated full audit log.

### Steps

**1. Real audit trails on detail pages**:
- `GET /api/bookings/{id}/audit` → map each entry to `<AuditTrail>` event shape
- `GET /api/expenses/{id}/audit` → same
- Handle all action types: `CREATE_BOOKING`, `ACTIVATE_DELEGATION`, `VIEW_BOOKINGS`, `CREATE_EXPENSE`, `APPROVE_EXPENSE`, `REJECT_EXPENSE`, `REVOKE_DELEGATION`

**2. Service health polling** (admin dashboard):
- Create `app/api/health/route.ts` — Next.js API route that fans out server-side to each service's `/actuator/health` endpoint and aggregates into a JSON array (avoids browser CORS)
- Admin dashboard polls this internal route
- Green dot = UP, red dot = DOWN; show latency if endpoint provides it

**3. `app/(app)/admin/audit/page.tsx`**:
- Full-width paginated table: Time, Action, Actor (blue text), Subject (amber text), Resource, Result badge (ALLOW=emerald, DENY=red)
- DENY rows: red-50 background
- Filters: action type, actor, result, date range
- Linked from "Full audit log →" button on admin dashboard

**4. `AuditTrail.tsx`** refinement: ensure all action type variants render with appropriate label and dot color.

### shadcn/ui components
`Alert`, `ScrollArea`

### Verification
- Create a booking → detail page timeline shows "Booking Created" event from real `/audit` endpoint
- Approve an expense → "Expense Approved" appears with approver name and timestamp
- Admin Service Health: stop one service → red dot appears within polling interval
- Admin audit log: DENY rows have red background; all filters work

---

## Phase 8 — Polish, Error Handling, Accessibility, and Final Config

### Goal
Production-grade quality: graceful error states everywhere, loading skeletons, form validation, accessibility audit pass.

### Steps

**1. Error handling**:
- 403: render `<AccessDenied>` component ("You don't have permission to view this resource" + "Go to Dashboard" link) — never a blank page
- 404: `not-found.tsx` per route segment for unknown booking/expense IDs
- Network errors: 1 retry on GET calls, then toast "Connection error — please try again"
- Expired delegation: `useDelegationContext` detects `expiresAt` → auto-deactivate with toast "Delegation session expired"

**2. Loading states**:
- `loading.tsx` in each route segment (Next.js convention)
- `<Skeleton>` components for stat cards, table rows, and detail panels

**3. Form validation** (zod schemas):
- Booking: destination required, departure required, return ≥ departure
- Expense: title required, at least 1 line item, all amounts > 0
- Delegation grant: delegate required, purpose required, at least 1 scope selected

**4. Accessibility**:
- All interactive elements have `aria-label` or visible text
- `StatusBadge` has `role="status"`
- All data tables have `aria-label` or `<caption>`
- `DelegationBanner` has `role="alert"`
- Focus returns to trigger element when modals close

**5. Final visual pass**: Side-by-side comparison of each mockup file against the implemented page. Verify:
- Delegation row tints (amber-50/10 → amber-50/30 hover) in all three tables (travel, expense, manager approvals)
- Status badge colors correct for all 8 status values
- "Showing N of M" pagination footer on list pages
- Type badges: Flight=sky, Hotel=violet, Car=teal

### Verification
- Kill BFF service → every page shows graceful error state, no uncaught exceptions
- Navigate to `/travel/BKG-DOES-NOT-EXIST` → 404 page with navigation link
- Submit booking form empty → inline validation errors appear
- Employee role visiting `/dashboard` with admin URL → AccessDenied renders
- Lighthouse accessibility audit: no critical violations

---

## Mockup Reference Files

All reference designs are at `frontend/mockups/`:

| Page | Mockup File | Key patterns |
|---|---|---|
| Login | `auth/login.html` | SSO button layout |
| Employee Dashboard | `dashboard/employee.html` | Stat cards, recent tables, delegation notice |
| Manager Dashboard | `dashboard/manager.html` | Approval queue, team delegation table |
| Admin Dashboard | `dashboard/admin.html` | Service health, audit event table |
| Booking List | `travel/list.html` | Delegation banner, amber row tint |
| Book Trip | `travel/book.html` | Radio card grid, locked source field, identity panel |
| Booking Detail | `travel/detail.html` | Identity & audit trail panel |
| Expense List | `expense/list.html` | Status filter toggle, mini stat strip |
| Submit Expense | `expense/submit.html` | Dynamic line items, running total, identity panel |
| Expense Detail | `expense/detail.html` | Approval chain, approve/reject buttons |
| Delegation Management | `delegation/manage.html` | Grant modal, activate button, consent table |

---

## Key Backend Files for Reference

| File | Purpose |
|---|---|
| `services/employee-bff/src/.../controller/DelegationBffController.java` | Token exchange activation/deactivation endpoints |
| `services/employee-bff/src/.../model/DelegationContext.java` | Shape of delegation context returned by BFF |
| `services/travel-service/src/.../controller/BookingController.java` | Booking API contract |
| `services/expense-service/src/.../controller/ExpenseController.java` | Expense API contract |
| `infrastructure/opa/policies/authorization.rego` | Authorization rules per role |
| `DELEGATION-FLOW.md` | Step-by-step end-to-end delegation walkthrough |
| `services/employee-bff/README.md` | BFF architecture and token exchange details |
