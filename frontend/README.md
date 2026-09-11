# K and K Media — Employee Payroll, Payslip & Leave Management System

An interactive front-end prototype of an Employee Management, Payroll,
Payslip and Leave Management System for **K and K Media (Pty) Ltd**.

This is a **prototype**: all data (employees, payroll figures, leave
requests) lives in-memory in the browser and resets on refresh. There is no
real backend, database, authentication, or email delivery yet — it exists
to demonstrate the intended workflows and screens before building the real
Spring Boot + MySQL system.

## What's in here

- **Login / Sign up** — sign-up requires a `@kandkmedia.co.za` email address
  and lets you choose a role: Employee, HR, or Admin.
- **Employee** — personal dashboard, payslip history, leave application and
  leave history.
- **HR** — company-wide dashboard, employee list, payroll pipeline
  (DRAFT → REVIEWED → APPROVED → FINALIZED → PUBLISHED → SENT), and leave
  approvals.
- **Admin** — system overview, company profile & payslip delivery settings,
  employee levels & departments, and user accounts.
- **IT Support** — a separate role (full system access, same screens as
  Admin) that defaults to an Office Issues view.
- **Manager** — team view with leave approvals for direct reports.
- **Support** — every role can submit a request about something **within
  the system itself** (a payslip that looks wrong, a leave application
  problem, a bug, an account/access issue) — sent directly server-side, no
  email client involved.
- **Office Issues** — a completely separate feature from Support, for
  **physical, on-site problems** at Midrand or Sandton (hardware, network,
  printers, equipment). IT Support/Admin get their own management view:
  change status (Open/In Progress/Resolved), write a response, and set an
  availability note ("At Midrand until 1pm, then Sandton") shown to
  employees before they report an issue.
- **Portal chooser** — employees (and anyone using "Switch to my profile")
  land on a "Choose a Portal" screen after login: **Payroll & Leave** or
  **IT Support**. The IT Support portal has three tabs — an IT Assistant
  chatbot, and direct links into Report a System Issue / Report an Office
  Issue.
- **IT Assistant** — a simple keyword-matched chatbot (no external AI
  calls) over an FAQ built from K and K Media's internal IT Operations
  Documentation (Outlook/Teams troubleshooting, printers, email setup,
  3CX) plus a few general quick-fix tips. See `IT_FAQ` in `App.jsx`.
- **HR Salary Structure** — HR can now edit each level's default
  salary/range inline, add entirely new levels/roles, and override an
  individual employee's salary directly from the Employees table (click
  the salary figure) — all of it feeds into payroll generation the same
  way the original seed data did.
- **Settings** — every user can edit their own profile, change their
  password, and set notification preferences.
- Anyone in HR, Admin, IT Support, or a Manager role can also **switch to
  their own employee profile** to apply for their own leave, since they're
  employees too.

## Running locally

```bash
npm install
npm run dev
```

Then open the local URL Vite prints (typically `http://localhost:5173`).

## Demo accounts

Password for all seed accounts: `password123`

| Role     | Email                              |
|----------|-------------------------------------|
| IT Support (full access) | carven.maceke@kandkmedia.co.za |
| HR       | lindiwe.zulu@kandkmedia.co.za       |
| Admin    | karabo.mahlangu@kandkmedia.co.za    |
| Manager  | thabo.nkosi@kandkmedia.co.za        |
| Employee | john.doe@kandkmedia.co.za           |

## Roadmap

See the original system specification for the full recommended build order
(Java + Spring Boot backend, MySQL database, JasperReports payslip PDFs,
scheduled email delivery, etc.). This repository currently covers the
front-end prototype only.
