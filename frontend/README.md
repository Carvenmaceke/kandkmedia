# K and K Media — Employee Payroll, Payslip & Leave Management System

The front-end for K and K Media's Employee Management, Payroll, Payslip and
Leave Management System.

**This is no longer a demo.** As of this version, all dummy/sample data
(employees, leave requests, demo login credentials) has been removed. The
app starts with zero employees — the first person to sign up becomes the
first real account. Everyone after that signs up the same way or is added
by HR.

Note the current architecture honestly: this frontend still runs entirely
on in-memory React state — nothing you enter here is sent to, or persisted
by, a server yet. Refreshing the page or opening it on another device
starts fresh. Making data actually persist (shared across devices, surviving
a refresh) requires connecting this frontend to the real backend in
`../backend` once that backend is deployed somewhere — see that folder's
README for exactly what's needed.

## What's in here

- **Login / Sign up** — sign-up requires a `@kandkmedia.co.za` or
  `@insideeducation.co.za` email (checked as you type, including whether it
  already has an account) and a 6-digit code emailed to that address.
- **Employee** — personal dashboard, payslip history, leave application and
  leave history.
- **HR** — company-wide dashboard, employee list, payroll pipeline
  (DRAFT → REVIEWED → APPROVED → FINALIZED → PUBLISHED → SENT), leave
  approvals, and an editable Salary Structure (default pay per level,
  add new levels, override an individual employee's salary).
- **Admin** — system overview, company profile & payslip delivery settings,
  employee levels & departments, and user accounts.
- **IT Support** — full system access (same screens as Admin), defaulting
  to the Office Issues management view.
- **Manager** — team view with leave approvals for direct reports.
- **Support** — for issues **within the system itself** (a payslip that
  looks wrong, a leave application problem, a bug, an account/access
  issue) — sent directly server-side, no email client involved.
- **Office Issues** — a separate feature for **physical, on-site
  problems** at Midrand or Sandton (hardware, network, printers,
  equipment), with its own management view and an availability note IT
  Support can set for employees to see before reporting an issue.
- **Portal chooser** — after login, choose **Payroll & Leave** or
  **IT Support** (assistant chatbot, or report a System/Office issue).
  Once chosen, only relevant navigation shows — switching requires
  pressing "Back to Portal Selection."
- **IT Assistant** — a keyword-matched chatbot (no external AI calls) built
  from K and K Media's internal IT Operations Documentation.
- **Settings** — every user can edit their basic profile, change their
  password, set notification preferences, and fill in the full onboarding
  record (personal info, tax, banking details, residential/postal
  address) in a dedicated "Personal & Payroll Information" section.

## Design & how the app works

- **Design system** — all colours, radii and shadows are CSS custom
  properties in `src/styles.css`; `App.jsx` reads them through the `T`
  token object. Typography is Inter (UI) + JetBrains Mono (IDs/figures).
- **Light / dark mode** — the theme button in the top bar cycles
  Light → Dark → System. The choice is remembered per browser and applied
  before first paint (no flash).
- **Responsive shell** — full-height sidebar + sticky top bar on desktop;
  on phones/tablets the sidebar becomes a slide-out menu and tables
  scroll horizontally.
- **Stay signed in** — refreshing the page restores your session from the
  stored JWT (until it expires) instead of sending you back to log in.
- **Real URLs** — every page has its own address (e.g. `#/payroll`,
  `#/settings`, `#/me/leave`), so the browser's Back/Forward buttons and
  refresh work as expected.
- **Toasts** — success/error messages appear as non-blocking toasts
  instead of browser `alert()` pop-ups.
- **Single page registry** — back-office pages are defined once in
  `PAGES` inside `App`, and each role's sidebar is just a list of page ids
  in `NAV_BY_ROLE`.
- **Smaller initial download** — the PDF library is loaded only when a
  PDF is actually generated.

Stack: React 19, Vite 8, lucide-react, jsPDF (lazy-loaded). Requires
Node.js 20.19+ or 22.12+.

## Running locally

```bash
npm install
npm run dev
```

Then open the local URL Vite prints (typically `http://localhost:5173`).

## Connecting to the real backend

`API_BASE_URL` in `src/App.jsx` defaults to the deployed backend
(`https://kandkmedia.onrender.com`). Override it per build with the
`VITE_API_BASE_URL` environment variable (e.g. in `.env.local`) — set it
to an empty string (`VITE_API_BASE_URL=`) for the offline, in-memory
mode. When it's set:

- **Login and Sign Up** call the real backend, store a JWT
  (`localStorage`), and fetch the logged-in person's real profile
- **HR/Admin/Master/IT Support** additionally fetch the full employee
  list on login
- **Salary edits, self-service profile edits, and Master's role changes**
  persist to the real database
- **Support tickets and Office Issues** were already wired (see below)
- **Payroll pipeline** (DRAFT → REVIEWED → APPROVED → FINALIZED → PUBLISHED
  → SENT) — generating/fetching drafts, advancing stages, and resending a
  failed payslip email all call the real backend

When `API_BASE_URL` is empty, every one of those falls back to the
original local-only, in-memory behavior — useful for frontend-only local
development without a backend running.

**Still local-only, not yet wired**: leave applications/approvals,
company settings, and departments/levels management. Password changes
have no backend endpoint at all yet. Payslip emails will only actually
send once real SMTP credentials (MAIL_HOST/MAIL_USERNAME/MAIL_PASSWORD)
are set on the backend host — the pipeline calling that code is wired,
but nothing sends until those are configured. See
`../backend/README.md` for the exact endpoint-by-endpoint status.

A backend-shaped mock server was used to verify this wiring's actual
request/response handling before it shipped (this sandbox can't reach
the live Render+Aiven stack directly) — the real end-to-end proof is
using the live site yourself.
