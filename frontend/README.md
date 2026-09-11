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
  Admin) that defaults to a Support Tickets view: manage every submitted
  request, change its status (Open/In Progress/Resolved), write a response
  back to the employee, and set an availability note employees see before
  submitting ("At Midrand until 1pm, then Sandton").
- **Manager** — team view with leave approvals for direct reports.
- **Help & Support** — every role can submit a support request (subject,
  category, priority, description, and which office — Midrand or Sandton —
  they're at) that's sent directly server-side, no email client involved.
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
