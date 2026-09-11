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

- **Login / Sign up** — sign-up requires a `@kandkmedia.co.za` email address
  and lets you choose a role: Employee, HR, Admin, or IT Support.
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

## Running locally

```bash
npm install
npm run dev
```

Then open the local URL Vite prints (typically `http://localhost:5173`).

## Connecting to the real backend

Set `API_BASE_URL` near the top of `src/App.jsx` to wherever the backend
(see `../backend`) ends up running, then rebuild. It's empty by default,
which is why Support/Office Issue submissions currently say "not
connected" — that's accurate, not a bug, until a backend is deployed and
this is pointed at it.
