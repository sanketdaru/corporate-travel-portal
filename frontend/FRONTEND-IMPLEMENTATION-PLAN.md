# Next.js Frontend — Corporate Travel Portal: Phased Implementation Plan

## Context

The backend for this corporate travel portal is fully built (Spring Boot microservices, 71/71 E2E tests passing). This plan covers building the React frontend from scratch, translating 13 static HTML mockups (at `frontend/mockups/`) into a production-grade Next.js 14 application with real API integration, Keycloak OIDC authentication, and a complex delegation/identity flow (RFC 8693 Token Exchange V2).

The frontend must surface a sophisticated identity model where an assistant (Dave) can act on behalf of an executive (Carol) via delegation + consent + token exchange — with the UI visually distinguishing delegated actions via amber banners and row tints.

---

## Implementation Status

| Phase | Description | Status |
|---|---|---|
| 0 | Dummy data seeding | ✅ Done |
| 1 | Project scaffolding, design system, app shell | ✅ Done |
| 2 | Authentication (Keycloak OIDC via NextAuth) | ✅ Done |
| 3 | Dashboard pages (all roles) | ✅ Done |
| 4 | Travel authorizations (list, create, detail) | ✅ Done |
| 5 | Expense management (list, submit, approve) | ✅ Done |
| 6 | Delegation and consent management | ✅ Done |
| 7 | Audit trails and admin dashboard | ✅ Done |
| 8 | Polish, error handling, accessibility | 🔲 Pending |

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
        │       │   ├── page.tsx             ← authorization list
        │       │   ├── book/page.tsx        ← new travel authorization form
        │       │   └── [id]/page.tsx        ← authorization detail + audit trail
        │       ├── expense/
        │       │   ├── page.tsx             ← expense list
        │       │   ├── submit/page.tsx      ← submit form with budget indicator
        │       │   └── [id]/page.tsx        ← expense detail
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
        │   ├── dashboard/
        │   │   ├── EmployeeDashboard.tsx
        │   │   └── ManagerDashboard.tsx
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
        │   ├── context/
        │   │   └── DelegationContext.tsx   ← React context + useDelegationContext hook
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
9. **Bookings are travel authorizations, NOT transactions**: A booking records intent to travel with a pre-approved budget. It has NO `bookingType` (FLIGHT/HOTEL/CAR) and NO `totalAmount`. Actual costs (flight ticket, hotel nights, meals, taxi) are captured as expense line items. Do NOT add booking type radio buttons or estimated amount fields to any booking form — they were removed in the refactor and must not return.
10. **Budget enforcement is two-layer**: Frontend disables "Submit for Approval" button when `runningTotal > approvedBudget`. Backend authoritatively enforces the ceiling in `ExpenseServiceImpl.submitExpense()` and returns HTTP 422 (`BudgetExceededException`) with `budget`/`total`/`overage`/`currency` fields in a RFC 7807 ProblemDetail.
11. **Submit Expense pre-fill via URL params**: The travel detail page passes these params when linking to `/expense/submit`: `bookingId`, `destination`, `businessPurpose`, `budget`, `budgetCurrency`, `startDate`. The submit form reads all six from `useSearchParams()` and uses them for both form defaults and the budget indicator.
12. **Internal budget endpoint**: `GET http://travel-service:8081/api/bookings/{id}/budget` is permitted without authentication (container-network-only, `permitAll()` in SecurityConfig). It returns `{ id, budget, budgetCurrency }`. Never call it from the frontend — it's for expense-service internal use only.

---

## Booking Type Reference (THE CORRECT MODEL)

```typescript
// lib/types/booking.ts — actual shape as of latest commit
export type BookingStatus = "PENDING" | "CONFIRMED" | "COMPLETED" | "CANCELLED" | "DRAFT";
export type BudgetCurrency = "INR" | "USD" | "EUR" | "SGD";

export interface Booking {
  id: string;
  tenantId: string;
  userId: string;           // owner of the trip
  createdBy: string;        // actor who created it (differs from userId when delegated)
  updatedBy?: string;
  destination: string;      // required
  startDate: string;        // ISO date "2026-05-10" — required
  endDate: string;          // ISO date "2026-05-17" — required
  businessPurpose?: string; // why the trip is being made
  notes?: string;           // visa info, special arrangements, etc.
  status: BookingStatus;
  budget: number;           // pre-approved spending ceiling — required
  budgetCurrency: BudgetCurrency;
  details?: string;         // JSON string for extra info
  createdAt: string;
  updatedAt: string;
  delegationId?: string;
}
```

**Fields that DO NOT EXIST on Booking** (do not reference them — they were removed):

- ~~`bookingType`~~ — belonged on expense line items, not the authorization
- ~~`totalAmount`~~ — replaced by `budget`
- ~~`currency`~~ — replaced by `budgetCurrency`

---

## API Quick Reference

| Action | Method | Endpoint |
|---|---|---|
| Active delegation context | GET | `BFF /api/bff/delegation/context` |
| Dashboard data | GET | `BFF /api/bff/dashboard` |
| List travel authorizations | GET | `BFF /api/bff/bookings` |
| Create travel authorization | POST | `BFF /api/bff/bookings` |
| Get travel authorization | GET | `BFF /api/bff/bookings/{id}` |
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

## Phase 0 — Dummy Data Seeding ✅ Done

### Goal
Seed the backend with realistic travel authorization data across 9 geographies so all booking and expense screens have meaningful content to display.

### What was seeded

Each booking is a **travel authorization** with `destination`, `businessPurpose`, `budget`, and `budgetCurrency`. There are no booking types (FLIGHT/HOTEL/CAR) — those were removed. Actual costs appear as expense line items.

| User | Authorizations | Expenses |
|---|---|---|
| alice.employee | London ₹2,00,000 (CONFIRMED), Tokyo ₹1,80,000 (CONFIRMED), Paris ₹1,50,000 (DRAFT) | London (APPROVED), Tokyo (DRAFT) |
| bob.manager | New York ₹2,20,000 (CONFIRMED), Sydney ₹1,20,000 (CONFIRMED) | NYC (SUBMITTED) |
| carol.executive | Berlin ₹1,60,000 (CONFIRMED), Dubai ₹1,20,000 (DRAFT) | Berlin (APPROVED) |
| carol via dave | Shanghai ₹2,00,000 (DRAFT), Bengaluru ₹50,000 (CONFIRMED) — delegated | Shanghai (DRAFT), Bengaluru (SUBMITTED) — delegated |
| dave.assistant | Sydney ₹2,00,000 (CONFIRMED) | — |
| eve.employee | Singapore ₹1,00,000 (CONFIRMED), Bengaluru ₹50,000 (DRAFT) — tenant-b | — |

The seed script is at `scripts/seed-data.sh`. Re-run with `bash scripts/seed-data.sh` (idempotent for delegations; creates new bookings/expenses on each run).

### Notes

- Source for all bookings: "Office HQ — Mumbai, India" (hardcoded display only, not stored)
- Expense items reference realistic categories: TRAVEL, ACCOMMODATION, MEALS, TRANSPORTATION
- Budget must be ≥ expected expense total for submitted expenses (enforced at submit time)

---

## Phase 1 — Project Scaffolding, Design System, and App Shell ✅ Done

### Goal
A running Next.js app with the full visual chrome — top nav, dark sidebar, delegation banner placeholder — that looks identical to the mockups with no live data and no auth enforcement.

### Phase 1 decisions

- `(app)/layout.tsx` is the AppShell: renders `<TopNav>` (h-14, fixed top, white), conditional `<DelegationBanner>` (h-10, amber-50, fixed below nav), `<Sidebar>` (w-64, fixed left, top varies), and `<main>` (ml-64, pt varies). Uses a `DelegationContextProvider` React context.
- `TopNav.tsx`: Left — TravelCorp logo. Right — role badge derived from `session.user.roles` (priority: manager=violet, admin=purple, executive=emerald, assistant=amber, employee=blue) + user name and avatar initials.
- `Sidebar.tsx`: bg-slate-900. Role-conditional sections from `session.user.roles`. Sign out at bottom.
- `DelegationBanner.tsx`: amber-50 bg, shows actor/subject/purpose, Exit button calls deactivate API.
- `StatusBadge.tsx`: Maps status → color (Confirmed/Approved/Active=emerald, Submitted/Pending=amber, Draft/Completed/Expired=slate, Rejected/Cancelled=red).

---

## Phase 2 — Authentication (Keycloak OIDC via NextAuth) ✅ Done

### Phase 2 decisions

- NextAuth v5 Keycloak provider; `callbacks.jwt` stores `access_token`; `callbacks.session` exposes `accessToken`, `user.roles`, `user.tenantId`, `user.name`, `user.email`.
- `middleware.ts` protects all `/(app)/*` routes.
- `lib/api/client.ts` Axios instance: `baseURL` = BFF, `withCredentials: true`, Bearer interceptor.
- `lib/context/DelegationContext.tsx`: calls `GET /api/bff/delegation/context` on mount; exposes `delegationActive`, `actorId`, `actorName`, `subjectId`, `subjectName`, `delegationId`, `consentId`, `purpose`.

---

## Phase 3 — Dashboard Pages (All Roles) ✅ Done

### Phase 3 decisions

- `dashboard/page.tsx` branches on `session.user.roles`. Priority: admin > manager > employee.
- **EmployeeDashboard**: 4 stat cards (Upcoming Trips, Open Expenses, Spent This Month, Delegations). Recent Trips table columns: Authorization ID, Destination, Dates, **Budget** (emerald text), Status. (No Type or Amount columns — removed in refactor.)
- **ManagerDashboard**: "Authorized Budget" stat card sums `booking.budget` across team bookings (not `totalAmount` — that field doesn't exist). Pending Approvals table with Approve/Reject buttons.
- Both dashboards call `GET /api/bff/dashboard` which returns `{ bookings, expenses }`.

---

## Phase 4 — Travel Authorizations (List, Create, Detail) ✅ Done

### Implemented UI — what it actually looks like

**Travel List (`/travel`)**:

- Page title: "Travel Authorizations" (not "My Trips")
- Table columns: Authorization (mono link), Destination, Travel Dates, Purpose (truncated), Budget (formatted), Created By (with "delegate" badge if `createdBy !== userId`), Status, View link
- Filters: Status dropdown + date range only — **no booking type filter** (removed)
- CTA button: "New Authorization" (or "Authorize for {subjectName}" when delegation active)

**Create Authorization (`/travel/book`)**:

- Page title: "Request Travel Authorization"
- Fields:
  - **Source** (read-only): "Office HQ — Mumbai, India"
  - **Destination** (required): text input with autocomplete from suggested list
  - **Departure Date** (required), **Return Date** (required, ≥ departure)
  - **Business Purpose** (required)
  - **Approved Budget** (required, > 0) + **Currency** select (INR/USD/EUR/SGD)
  - **Notes** (optional textarea)
- **NO booking type radio buttons** — these were removed in the refactor
- Identity context panel at bottom showing userId/createdBy/delegationId/tenantId
- POST payload: `{ destination, startDate, endDate, businessPurpose, notes, budget, budgetCurrency, tenantId, userId, status: "PENDING" }`

**Authorization Detail (`/travel/[id]`)**:

- Breadcrumb: "Travel Authorizations" → ID
- Header: `{destination} — {businessPurpose}` + StatusBadge
- Sub-header: `{id} · {startDate} – {endDate}` (no type segment)
- Card title: "Authorization Details" (not "Booking Details")
- Detail rows: Source, Destination, Travel dates, **Approved Budget** (emerald badge), Business purpose (if present), Notes (if present), Created
- **NO "Booking type" row** and **NO "Total amount" row** — removed in refactor
- "Submit Expense" button links to:
  `/expense/submit?bookingId={id}&destination={enc}&businessPurpose={enc}&budget={n}&budgetCurrency={c}&startDate={d}`
- Identity & Audit Trail panel (amber, shown only if `booking.delegationId` is set)
- Event Timeline: `<AuditTrail>` pulling from `GET /api/bookings/{id}/audit`

---

## Phase 5 — Expense Management (List, Submit, Detail) ✅ Done

### Implemented UI

**Expense List (`/expense`)**:

- Summary strip: 4 mini stat cards (Total Submitted, Approved & Paid, Pending Approval, Draft)
- Status filter toggle
- Table columns: Report ID, Title, Trip (mono link), Items count, Total, Submitted, Status, Actions

**Submit Expense (`/expense/submit`)**:

- Pre-fills from URL params when coming from the travel detail "Submit Expense" button:
  - `title`: `{destination} — {businessPurpose}`
  - `description`: `{businessPurpose}`
  - `bookingId`: pre-selects the linked trip
  - `currency`: `{budgetCurrency}` from the authorization
  - First item `date`: `{startDate}` from the authorization
  - `approvedBudget` state: `{budget}` — drives the budget indicator
- **Budget indicator** (shown in running total footer when `approvedBudget > 0`):
  - Green panel: "Approved budget: ₹X" + "Remaining: ₹Y"
  - Red panel when over: "Approved budget: ₹X" + "Over budget by: ₹Z"
- **"Submit for Approval" button is disabled** when `runningTotal > approvedBudget`
- 422 error handling: parses `{ budget, total, overage, currency }` from backend and shows: "Expense total ₹X exceeds the approved travel budget of ₹Y (over by ₹Z). Reduce your items or request a higher budget."
- Linked Trip select still shows all bookings (for standalone expenses not pre-filled from a trip)
- When arriving without URL params but with a `bookingId`: fetches the booking to get `budget`/`budgetCurrency` for the indicator

**Expense Detail (`/expense/[id]`)**:

- Header: title + status badge + expense ID
- Report Details card + Expense Items table with total footer
- Approval Chain card (if submitted/approved/rejected)
- Approve/Reject buttons only when `session.user.roles.includes('manager')`
- Identity & Audit Trail panel (if `expense.delegationId !== null`)
- Event Timeline from `GET /api/expenses/{id}/audit`

---

## Phase 6 — Delegation and Consent Management ✅ Done

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

### Verification (Phase 6)

- Executive role user: "Granted by Me" shows existing delegation; Revoke works; Grant modal creates new delegation
- Assistant role user: "Granted to Me" shows delegations; clicking "Activate" → amber banner appears immediately
- Banner shows delegation ID, purpose, consent ID, expiry correctly
- Exit → banner disappears, sidebar shifts back to normal top position
- Travel list while delegation active: subtitle shows delegatee's name

---

## Phase 7 — Audit Trails and Admin Dashboard ✅ Done

### Goal
Live audit trail data on detail pages; admin dashboard with real service health polling; paginated full audit log.

### Steps

**1. Real audit trails on detail pages**:

- `GET /api/bookings/{id}/audit` → map each entry to `<AuditTrail>` event shape
- `GET /api/expenses/{id}/audit` → same
- Handle all action types: `CREATE`, `ACTIVATE_DELEGATION`, `VIEW`, `UPDATE`, `SUBMIT`, `APPROVE`, `REJECT`, `REVOKE_DELEGATION`

**2. Service health polling** (admin dashboard):

- Create `app/api/health/route.ts` — Next.js API route that fans out server-side to each service's `/actuator/health` endpoint and aggregates into a JSON array (avoids browser CORS)
- Admin dashboard polls this internal route
- Green dot = UP, red dot = DOWN; show latency if endpoint provides it

**3. `app/(app)/admin/audit/page.tsx`**:

- Full-width paginated table: Time, Action, Actor (blue text), Subject (amber text), Resource, Result badge (ALLOW=emerald, DENY=red)
- DENY rows: red-50 background
- Filters: action type, actor, result, date range
- Linked from "Full audit log →" button on admin dashboard

### Verification (Phase 7)

- Create a booking → detail page timeline shows "Booking Created" event from real `/audit` endpoint
- Approve an expense → "Expense Approved" appears with approver name and timestamp
- Admin Service Health: stop one service → red dot appears within polling interval
- Admin audit log: DENY rows have red background; all filters work

---

## Phase 8 — Polish, Error Handling, Accessibility, and Final Config 🔲 Pending

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

- Authorization: destination required, departure required, return ≥ departure, budget > 0
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
- **Note**: The mockup shows FLIGHT/HOTEL/CAR type badges — these are obsolete. The implemented UI shows Budget (emerald) instead.

### Verification (Phase 8)

- Kill BFF service → every page shows graceful error state, no uncaught exceptions
- Navigate to `/travel/BKG-DOES-NOT-EXIST` → 404 page with navigation link
- Submit authorization form empty → inline validation errors appear
- Employee role visiting admin URL → AccessDenied renders
- Lighthouse accessibility audit: no critical violations

---

## Mockup Reference Files

All reference designs are at `frontend/mockups/`:

| Page | Mockup File | Notes |
| --- | --- | --- |
| Login | `auth/login.html` | SSO button layout |
| Employee Dashboard | `dashboard/employee.html` | Stat cards, recent tables, delegation notice. **Ignore** FLIGHT/HOTEL/CAR type badges — replaced by Budget |
| Manager Dashboard | `dashboard/manager.html` | Approval queue, team delegation table |
| Admin Dashboard | `dashboard/admin.html` | Service health, audit event table |
| Booking List | `travel/list.html` | **Ignore** type filter and type column — replaced by Purpose + Budget |
| Book Trip | `travel/book.html` | **Ignore** booking type radio buttons and amount — replaced by Approved Budget. Keep: locked source field, destination, dates, identity panel |
| Booking Detail | `travel/detail.html` | **Ignore** "Booking type" and "Total amount" rows — replaced by "Approved budget" row |
| Expense List | `expense/list.html` | Status filter toggle, mini stat strip |
| Submit Expense | `expense/submit.html` | Dynamic line items, running total + **budget indicator**, identity panel |
| Expense Detail | `expense/detail.html` | Approval chain, approve/reject buttons |
| Delegation Management | `delegation/manage.html` | Grant modal, activate button, consent table |

---

## Key Backend Files for Reference

| File | Purpose |
|---|---|
| `services/employee-bff/src/.../controller/DelegationBffController.java` | Token exchange activation/deactivation endpoints |
| `services/employee-bff/src/.../model/DelegationContext.java` | Shape of delegation context returned by BFF |
| `services/travel-service/src/.../controller/BookingController.java` | Booking/authorization API contract |
| `services/travel-service/src/.../model/entity/Booking.java` | Canonical booking entity — source of truth for field names |
| `services/travel-service/src/main/resources/db/migration/V3__refactor_booking_to_travel_authorization.sql` | DB migration that removed bookingType/totalAmount |
| `services/travel-service/src/main/resources/db/migration/V4__add_business_purpose_and_notes_to_bookings.sql` | DB migration that added businessPurpose/notes |
| `services/expense-service/src/.../controller/ExpenseController.java` | Expense API contract |
| `services/expense-service/src/.../exception/BudgetExceededException.java` | 422 error shape: budget/total/overage/currency |
| `services/expense-service/src/.../client/TravelServiceClient.java` | Internal service-to-service budget fetch |
| `infrastructure/opa/policies/authorization.rego` | Authorization rules per role |
| `DELEGATION-FLOW.md` | Step-by-step end-to-end delegation walkthrough |
| `services/employee-bff/README.md` | BFF architecture and token exchange details |
| `frontend/nextjs/lib/types/booking.ts` | Canonical TypeScript booking type — check here before coding |
