# K and K Media — Payroll System Backend

Spring Boot + MySQL implementation of the Employee Payroll, Payslip and
Leave Management System, matching the roles and workflow used in the
[`frontend`](../frontend).

This is a real system now, not a demo — `DataSeeder` only creates
structural reference data (the company profile, departments, salary
levels, leave types); there are no seeded people or user accounts. The
first real account is created via Sign Up.

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
employee levels, and leave types — no people, no user accounts. Sign up
via `POST /api/auth/signup` to create the first real account (any role
except Manager, which is assigned internally by HR/Admin/IT Support
after the fact).

## Using a managed MySQL provider (e.g. Aiven)

Managed MySQL providers give you a connection string like:

```
mysql://<user>:<password>@<host>:<port>/<database>?ssl-mode=REQUIRED
```

Spring needs the JDBC form instead — same pieces, different shape:

```bash
export DB_URL="jdbc:mysql://<host>:<port>/<database>?sslMode=REQUIRED"
export DB_USERNAME=<user>
export DB_PASSWORD=<password>
```

Set these as actual environment variables on wherever you deploy this
(Render, Railway, Fly.io, etc.) — never commit real credentials into
`application.yml` or anywhere else in this repo, since it's public.

## Auth

`POST /api/auth/signup` and `POST /api/auth/login` return a JWT. Send it as
`Authorization: Bearer <token>` on every other request.

Signup rejects any email that doesn't end in `@kandkmedia.co.za`
(configurable via the `ALLOWED_EMAIL_DOMAIN` env var) and rejects `MANAGER`
as a chosen role — a manager is promoted by HR, not self-selected.

## Route map (mirrors the frontend's role split)

| Prefix           | Who can call it                  | What's there                                   |
|-------------------|-----------------------------------|-------------------------------------------------|
| `/api/auth/**`     | Anyone                            | signup (always creates an EMPLOYEE — see below), login |
| `/api/me/**`       | Any authenticated user            | own profile (`GET`/`PUT /profile`), leave, leave balance, payslips |
| `/api/manager/**`  | MASTER, MANAGER, HR, ADMIN, IT_SUPPORT | team leave requests, approve/reject        |
| `/api/hr/**`       | MASTER, HR, ADMIN, IT_SUPPORT        | employee list, `PUT /employees/{id}/profile`, payroll pipeline, all leave |
| `/api/admin/**`    | MASTER, ADMIN, IT_SUPPORT            | company profile, departments, levels, users, onboarding docs |

`/api/me/**` is what backs the frontend's "switch to my profile" — HR,
Admin, Manager and IT Support accounts hit the exact same self-service
endpoints an Employee-role account does.

### Role assignment is Master-exclusive

`SignupRequest` has no `role` field at all — every signup becomes
`Role.EMPLOYEE`, enforced server-side in `AuthService`, never trusted from
the client. This closes what would otherwise be a real vulnerability: if
the client could say "make me an Admin" at signup, anyone could grant
themselves access to everyone's personal/banking/tax information.

The only way to become HR, Admin, IT Support, or Manager is via
`PUT /api/admin/users/{id}/role`, which is restricted with
`@PreAuthorize("hasRole('MASTER')")` specifically — not just covered by
the broader `/api/admin/**` rule that also lets ADMIN/IT_SUPPORT in, since
letting any admin-tier account grant further admin access would defeat
the point of having a single gatekeeper. It also refuses to change the
Master account's own role, and refuses to promote anyone else to Master.

`DataSeeder` creates exactly one real account — the Master/system owner
(`carven.maceke@kandkmedia.co.za`, password `password123` by default,
change it) — since without at least one privileged account, nobody could
grant roles to anyone else. Every other person in the system should sign
up themselves and be assigned a role by Master afterward.

`PUT /api/me/profile` and `PUT /api/hr/employees/{id}/profile` both take an
`EmployeeProfileDto` — the full onboarding field set (personal info, tax,
banking, residential/postal address) added to `Employee`. Every field is
optional; only non-null ones are applied, so filling in a profile section
at a time never wipes out fields entered earlier.

**Signup itself now requires most of that same onboarding info up front**
(not deferred to a profile edit afterward) — see the `@NotBlank` fields on
`SignupRequest`. It also requires `agreedToTerms: true` and a `signature`
(a PNG data URL from a signature pad), matching the frontend's
draw-to-sign requirement. Employee Level is no longer part of signup at
all — every new account starts with `salary: 0`; HR sets the real salary
per employee afterward via `PUT /api/hr/employees/{id}/profile` (or the
frontend's "click the salary to edit" flow), and that's what's used the
next time payroll is generated for them.

`GET /api/hr/employees/{id}/onboarding-document` returns a PDF (Apache
PDFBox, see `OnboardingDocumentPdfService`) containing everything the
employee entered at signup, plus their signature — this is what backs the
frontend's "Download Onboarding Document" button in the employee profile
drawer.

## Making payslip emails actually send

This isn't a stub — `EmailService` sends via **Resend's HTTPS API**
(`https://api.resend.com/emails`), building the payslip PDF via
`PayslipPdfService` (Apache PDFBox) and attaching it as base64. It sends
automatically for every employee the moment HR advances a payroll batch to
the `SENT` stage (`POST /api/hr/payroll/advance`), and can be retried
per-employee via `POST /api/hr/payroll/{id}/resend-email`.

**Why HTTPS and not SMTP**: this used to go through Spring's `JavaMailSender`
over raw SMTP, but Render (and many other hosts) block outbound SMTP ports
(25/465/587) as an anti-spam measure. That surfaced as
`MailConnectException: Couldn't connect to host... Connection timed out` —
which looks like a credentials problem but isn't; it's the platform
refusing the TCP connection outright. HTTPS (443) is never blocked this
way, so the API is the reliable path regardless of host.

To make it actually deliver mail, you need to supply:

1. **A Resend API key**, set as an environment variable. It's reused from
   the same `MAIL_PASSWORD` variable the old SMTP setup used — no new
   variable name needed:
   ```bash
   export MAIL_PASSWORD=your-resend-api-key   # starts with re_ — set this
                                                # only as an env var on
                                                # wherever you deploy, never
                                                # committed to the repo
   ```
   `MAIL_HOST`/`MAIL_PORT`/`MAIL_USERNAME` are no longer used by
   `EmailService` (they were SMTP-specific) — only `MAIL_PASSWORD` (the
   Resend key) and `MAIL_FROM` (see below) matter now.

2. **A "From" address**, via `MAIL_FROM` (see `app.mail-from` in
   `application.yml`):
   ```bash
   export MAIL_FROM=payroll@kandkmedia.co.za
   ```
   Resend requires the sending domain to be **verified** in their
   dashboard before it'll deliver from that address to arbitrary
   recipients — until `kandkmedia.co.za` (or whichever domain) is verified,
   use Resend's own test domain instead (no verification needed):
   ```bash
   export MAIL_FROM=onboarding@resend.dev
   ```

3. **A place to actually run this backend continuously.** GitHub Pages
   (where the `frontend` prototype is deployed) only serves static files —
   it cannot run a Java process, so this backend needs real hosting. Any of
   these have a free tier that works for testing: Render, Railway, Fly.io,
   or a small VPS. You'd also need a reachable MySQL instance (Railway,
   PlanetScale, or a managed MySQL add-on on whichever host you pick).

4. Set `JWT_SECRET`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` as covered above,
   alongside `MAIL_PASSWORD`/`MAIL_FROM`, wherever you deploy it.

Until those exist, sending fails gracefully — the HTTP error is caught and
recorded on the `Payroll` row's `emailFailureReason` field rather than
crashing the request — so the rest of the app keeps working even with mail
unconfigured, you'll just see the
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

Implemented: auth + JWT (including IT_SUPPORT, added to match the
frontend's role), the full onboarding field set on `Employee` (personal
info, tax, banking, residential/postal address — see `EmployeeProfileDto`),
employee/department/level/company data model — all editable in-app, no
seeded people, leave apply/approve/reject with balance deduction and
signed approval/decline letters (see above), a payroll draft calculation,
the DRAFT → REVIEWED → APPROVED → FINALIZED → PUBLISHED → SENT pipeline,
real PDF payslip generation (Apache PDFBox) with the security/verification
features described above, real email delivery with the PDF attached
(triggered when a batch reaches SENT, or manually via resend), an
automatic scheduled month-end run that triggers it without HR needing to
click anything, and a direct (no email client) Help & Support request
endpoint — see "Making payslip emails actually send", "Automatic
month-end payslip run", and "Help & Support requests" above for what's
needed to switch each on.

**Deployed and connected**: this backend now runs live on Render, connected
to a real MySQL database (Aiven), and the frontend is wired to it for
authentication (login/signup issue real JWTs), fetching the logged-in
person's profile and the full employee list, salary edits, self-service
profile edits, Master's role-change, and Support/Office Issue
submissions — see `frontend/README.md` for exactly what's connected vs
still local-only.

**Still not wired to the frontend**: leave applications/approvals, the
payroll pipeline, company settings, and departments/levels management —
these endpoints all exist and work, but nothing on the frontend calls
them yet. There's also no password-change endpoint yet, so that stays
local-only until one exists.

Not yet implemented (see the original spec's Phase 5–8 for the intended
shape, and "Payslip document security" above for the security-specific gaps):

- Moving `/api/public/support` and `/api/public/office-issues` behind
  real authentication once the frontend actually has a session to attach
  — public for now because the frontend has no real login flow talking
  to this backend yet
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
