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

## What's implemented vs. still TODO

Implemented: auth + JWT, employee/department/level/company data model, leave
apply/approve/reject with balance deduction, a payroll draft calculation and
the DRAFT → REVIEWED → APPROVED → FINALIZED → PUBLISHED → SENT pipeline.

Not yet implemented (see the original spec's Phase 5–8 for the intended
shape):

- PDF payslip generation (spec recommends JasperReports against the
  `Payroll` row once `FINALIZED`)
- Scheduled monthly payslip email job + delivery/retry logging
- Notifications persistence (the frontend currently fakes these client-side)
- Fine-grained request validation/DTOs for every admin write endpoint
- Test suite
- Audit logging, rate limiting, and other production hardening from
  the spec's "Phase 8 — Production Improvements"

The PAYE/UIF figures in `PayrollService` are simplified placeholders (flat
15% / 1% capped) — the same illustrative numbers the frontend prototype
uses — and need a real SARS-compliant tax table before this touches a real
payslip.
