# K and K Media — Payroll System Backend

Spring Boot + MySQL implementation of the Employee Payroll, Payslip and
Leave Management System, matching the roles and workflow used in the
[`frontend`](../frontend) prototype.

## Stack

- Java 17, Spring Boot 3
- Spring Web, Spring Data JPA, Spring Security (stateless JWT), Bean Validation
- MySQL
- Lombok

## Running locally

1. Have a MySQL server running and create a database (or let
   `createDatabaseIfNotExist=true` in `application.yml` do it for you).
2. Set environment variables (or edit `application.yml` directly for local dev):

   ```bash
   export DB_URL="jdbc:mysql://localhost:3306/kandkmedia_payroll?createDatabaseIfNotExist=true"
   export DB_USERNAME=root
   export DB_PASSWORD=yourpassword
   export JWT_SECRET="a-long-random-string-at-least-32-bytes"
   ```

3. Run it:

   ```bash
   cd backend
   mvn spring-boot:run
   ```

On first run, `DataSeeder` populates the company profile, departments,
employee levels, leave types, and the same demo accounts the frontend
prototype uses (password `password123` for all of them):

| Role     | Email                              |
|----------|-------------------------------------|
| HR       | lindiwe.zulu@kandkmedia.co.za       |
| Admin    | karabo.mahlangu@kandkmedia.co.za    |
| Manager  | thabo.nkosi@kandkmedia.co.za        |
| Employee | john.doe@kandkmedia.co.za           |

## Auth

`POST /api/auth/signup` and `POST /api/auth/login` return a JWT. Send it as
`Authorization: Bearer <token>` on every other request.

Signup rejects any email that doesn't end in `@kandkmedia.co.za`
(configurable via the `ALLOWED_EMAIL_DOMAIN` env var) and rejects `MANAGER`
as a chosen role — a manager is promoted by HR, not self-selected.

## Route map (mirrors the frontend's role split)

| Prefix           | Who can call it                  | What's there                                   |
|-------------------|-----------------------------------|-------------------------------------------------|
| `/api/auth/**`     | Anyone                            | signup, login                                   |
| `/api/me/**`       | Any authenticated user            | own profile, leave, leave balance, payslips     |
| `/api/manager/**`  | MANAGER, HR, ADMIN                 | team leave requests, approve/reject             |
| `/api/hr/**`       | HR, ADMIN                          | employee list, payroll pipeline, all leave      |
| `/api/admin/**`    | ADMIN                              | company profile, departments, levels, users     |

`/api/me/**` is what backs the frontend's "switch to my profile" — HR,
Admin and Manager accounts hit the exact same self-service endpoints an
Employee-role account does.

## Making payslip emails actually send

This isn't a stub — `EmailService` uses Spring's real `JavaMailSender` to
build the payslip PDF (via `PayslipPdfService`, Apache PDFBox) and send it
as an attachment. It sends automatically for every employee the moment HR
advances a payroll batch to the `SENT` stage
(`POST /api/hr/payroll/advance`), and can be retried per-employee via
`POST /api/hr/payroll/{id}/resend-email`.

To make it actually deliver mail, you need to supply:

1. **Real SMTP credentials**, set as environment variables:
   ```bash
   export MAIL_HOST=smtp.gmail.com      # or your provider's SMTP host
   export MAIL_PORT=587
   export MAIL_USERNAME=your-sending-address@kandkmedia.co.za
   export MAIL_PASSWORD=your-app-password
   ```
   If you don't have a company mailbox with SMTP access yet, a free
   transactional-email provider (e.g. Mailgun, Resend, Brevo/Sendinblue)
   works too — swap the host/port for whatever they give you. If you use
   Gmail directly, you need an **App Password** (Google Account → Security
   → 2-Step Verification → App passwords), not your normal login password.

2. **A place to actually run this backend continuously.** GitHub Pages
   (where the `frontend` prototype is deployed) only serves static files —
   it cannot run a Java process, so this backend needs real hosting. Any of
   these have a free tier that works for testing: Render, Railway, Fly.io,
   or a small VPS. You'd also need a reachable MySQL instance (Railway,
   PlanetScale, or a managed MySQL add-on on whichever host you pick).

3. Set `JWT_SECRET`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` as covered above,
   alongside the mail vars, wherever you deploy it.

Until both of those exist, `mailSender.send(...)` will throw a connection
error, which `EmailService` catches and records on the `Payroll` row's
`emailFailureReason` field rather than crashing the request — so the rest
of the app keeps working even with mail unconfigured, you'll just see the
failure reason on that record instead of a successful send.

**The frontend prototype does not call any of this yet** — it's still
running entirely on in-memory dummy data (see `frontend/README.md`).
Wiring the frontend to call this live API instead of its local state is
the next integration step once this backend is deployed somewhere real.

## What's implemented vs. still TODO

Implemented: auth + JWT, employee/department/level/company data model, leave
apply/approve/reject with balance deduction, a payroll draft calculation,
the DRAFT → REVIEWED → APPROVED → FINALIZED → PUBLISHED → SENT pipeline,
real PDF payslip generation (Apache PDFBox), and real email delivery with
the PDF attached (triggered when a batch reaches SENT, or manually via
resend) — see "Making payslip emails actually send" above for what's
needed to switch it on.

Not yet implemented (see the original spec's Phase 5–8 for the intended
shape):

- A **scheduled** monthly job that triggers the send automatically at
  month-end — right now sending only happens when HR manually advances
  the pipeline to SENT, not on a timer
- An email delivery **log** table (spec section 14) — success/failure is
  currently only stored on the `Payroll` row itself, one entry per employee
  per period, not a full audit history of attempts
- Notifications persistence (the frontend currently fakes these client-side)
- Fine-grained request validation/DTOs for every admin write endpoint
- Test suite
- Audit logging, rate limiting, and other production hardening from
  the spec's "Phase 8 — Production Improvements"

The PAYE/UIF figures in `PayrollService` are simplified placeholders (flat
15% / 1% capped) — the same illustrative numbers the frontend prototype
uses — and need a real SARS-compliant tax table before this touches a real
payslip.
