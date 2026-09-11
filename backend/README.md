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

   **Using Resend specifically**: Resend exposes an SMTP relay, so no code
   changes are needed — just point the same env vars at it:
   ```bash
   export MAIL_HOST=smtp.resend.com
   export MAIL_PORT=587
   export MAIL_USERNAME=resend
   export MAIL_PASSWORD=your-resend-api-key   # starts with re_ — set this
                                                # only as an env var on
                                                # wherever you deploy, never
                                                # committed to the repo
   ```
   Resend's free tier only sends to your own verified email/domain until
   you verify a sending domain — until `kandkmedia.co.za` (or whichever
   domain you use) is verified in Resend's dashboard, mail to other
   addresses will be rejected by Resend even with a valid key.

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

## Automatic month-end payslip run

`PayslipSchedulerService` runs once a day and, only on the configured
trigger day, generates any still-missing draft payroll rows for the
current period and walks every record through to `SENT` — which is what
actually fires the real emails, same code path as HR manually advancing
the pipeline. Configurable via env vars:

```bash
export PAYSLIP_AUTO_SEND=true          # set false to disable entirely
export PAYSLIP_CRON_HOUR=18            # 24h, server time
export PAYSLIP_CRON_MINUTE=0
export PAYSLIP_SEND_ON=LAST_DAY_OF_MONTH   # or DAY_BEFORE_MONTH_END
```

Important behavior to know before relying on this: it does **not** wait
for HR review. Whatever's still sitting in DRAFT on the trigger day goes
out with zero overtime/bonus (since nobody entered any); anything HR has
already progressed further gets carried through from wherever it is. If
you want HR's variable-earnings review to always happen first, that
review needs to be completed earlier in the month — the scheduler is a
safety net that guarantees everyone gets paid on time, not a substitute
for the review step.

## Help & Support requests

`POST /api/public/support` sends a support request directly, server-side —
no `mailto:`, no email client. The frontend's Support form calls this
directly and shows exactly what happened (sent / not connected / server
rejected it / network failure) rather than assuming success.

It's deliberately public (no JWT) because the frontend prototype has no
real authenticated session to attach — submitter identity rides along in
the request body instead. If this backend later gets real frontend-driven
login, moving this behind `/api/me/support` and deriving the submitter
from the authenticated user would be the natural hardening step; noted in
the TODO list below rather than done now.

Every ticket is saved (`SupportTicket`, status `OPEN`/`RESOLVED`) before
the email send is even attempted, so a bad mail config never loses the
submission — same catch-and-record pattern as the other email methods,
via `SupportTicket.emailSent` / `emailFailureReason`. Admin can see every
ticket and mark them resolved at `GET /api/admin/support` and
`PUT /api/admin/support/{id}/resolve`.

Sent to `${SUPPORT_EMAIL:itsupport@kandkmedia.co.za}` — override via the
`SUPPORT_EMAIL` env var. Needs the same SMTP setup as payslip emails (see
"Making payslip emails actually send" above) to actually deliver.

**To connect the deployed frontend to this**: set `API_BASE_URL` near the
top of `frontend/src/App.jsx` to wherever this backend ends up running
(e.g. `https://api.kandkmedia.co.za`), then rebuild and redeploy the
frontend. It's empty by default — the Support form says plainly that it
isn't connected yet rather than pretending a click did something.

## Payslip document security

Each payslip PDF, once finalized, carries:

- A unique **Payslip ID** (`PAY-2026-09-000005`) — never the raw database
  row ID.
- A public **Verification Code** (`7F4K-92MX`).
- A **SHA-256 hash** of the generated PDF, so a modified copy can be
  detected.
- A **generation timestamp**.
- A **QR code** encoding a link to the verification endpoint below.
- Full employer identifying details (name, address, registration number)
  alongside the employee's name, position, and pay period, per the
  Department of Employment and Labour's payslip content guidance.
- An "electronically generated, no physical signature required" notice —
  worded to state that plainly without claiming a legal electronic
  signature under the ECT Act, since none is actually implemented.

All of these are generated **exactly once**, when a payroll row is
finalized (`PayrollService.sealPayslip`, called from `advanceStage`) — not
regenerated on every download, which is what makes the hash and ID
trustworthy as an unmodified-since-issued check in the first place.

**Verifying a payslip**: `GET /api/public/verify/{verificationCode}` is
unauthenticated (anyone with the code can check it — that's the point) and
returns only a masked employee name, employer, payslip ID, pay period, and
a valid/not-found status — never salary figures or anything else sensitive
from the payslip. This is a JSON API, not a webpage; scanning the QR code
today shows raw JSON in a browser, not the polished "✅ Document Verified"
page a real verification flow would want. Building that page is a small,
separate frontend task, not yet done.

**Not yet done, and worth knowing before this is exposed publicly:**
- **Rate limiting on `/api/public/verify/**`** — it's unauthenticated by
  design, which also means it's brute-forceable against the verification
  code space without a rate limit in front of it. Flagged in the
  controller's Javadoc too, not just here.
- Other endpoints (`/api/hr/employees/{id}`, `/api/hr/payroll/{id}/...`)
  still take the raw database ID in the URL — only the verification
  endpoint was switched to an opaque public identifier so far. Widening
  that to every endpoint is a real hardening task, not done yet.
- MFA is not implemented. Adding TOTP-based 2FA is a meaningfully sized
  feature on its own (secret generation, enrollment QR, verification step
  at login) — flagged here rather than bolted on partially.
- No audit log table, no database-level encryption at rest, no automated
  key rotation for the JWT secret.

## Signed leave approval/decline letters

Same pattern as payslips: applying for leave requires a signature
(`LeaveRequestDto.signature`, a PNG data URL from the frontend's canvas
signature pad — rejected by validation if missing). Approving or declining
(`PUT /api/manager/team-leave/{id}/approve|reject`, or the equivalent
`/api/hr/leave/{id}/...` routes) requires a `LeaveDecisionDto` body with the
decider's own signature; declining additionally requires a non-blank
`reason`, enforced in `LeaveService.decide`.

Once decided, `LeaveLetterPdfService` generates a signed approval/decline
letter (Apache PDFBox) with both signature images embedded, and
`EmailService.sendLeaveLetter` emails it to the applicant — same
catch-and-record-the-failure behavior as payslip emails
(`letterEmailSent` / `letterEmailFailureReason` on the `LeaveRequest` row),
and needs the same SMTP setup described above to actually deliver.

## What's implemented vs. still TODO

Implemented: auth + JWT, employee/department/level/company data model, leave
apply/approve/reject with balance deduction and signed approval/decline
letters (see above), a payroll draft calculation,
the DRAFT → REVIEWED → APPROVED → FINALIZED → PUBLISHED → SENT pipeline,
real PDF payslip generation (Apache PDFBox) with the security/verification
features described above, real email delivery with the PDF attached
(triggered when a batch reaches SENT, or manually via resend), an
automatic scheduled month-end run that triggers it without HR needing to
click anything, and a direct (no email client) Help & Support request
endpoint — see "Making payslip emails actually send", "Automatic
month-end payslip run", and "Help & Support requests" above for what's
needed to switch each on.

Not yet implemented (see the original spec's Phase 5–8 for the intended
shape, and "Payslip document security" above for the security-specific gaps):

- Moving `/api/public/support` behind real authentication once the
  frontend has a genuine login flow talking to this backend — it's public
  for now because the frontend prototype has no real session to attach
- Rate limiting on `/api/public/support` (same brute-force concern as the
  verification endpoint below — it's public and unauthenticated)
- An email delivery **log** table (spec section 14) — success/failure is
  currently only stored on the `Payroll` row itself, one entry per employee
  per period, not a full audit history of attempts
- A human-facing payslip verification webpage (the QR currently resolves
  to a JSON API response)
- Rate limiting on the public verification endpoint
- MFA
- Notifications persistence (the frontend currently fakes these client-side)
- Fine-grained request validation/DTOs for every admin write endpoint
- Test suite
- Audit logging, database encryption at rest, and other production
  hardening from the spec's "Phase 8 — Production Improvements"

The PAYE/UIF figures in `PayrollService` are simplified placeholders (flat
15% / 1% capped) — the same illustrative numbers the frontend prototype
uses — and need a real SARS-compliant tax table before this touches a real

payslip.
