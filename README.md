# K and K Media — HR, Payroll & Leave Management System

Employee Management, Payroll, Payslip and Leave Management System for
**K and K Media (Pty) Ltd**.

This repository is split into two independent, separately-runnable projects:

```
kandkmedia/
├── frontend/   React + Vite prototype — all four role dashboards
│               (Employee, Manager, HR, Admin), login/signup, settings.
│               In-memory dummy data, no backend required to run it.
│               See frontend/README.md.
│
├── backend/    Spring Boot + MySQL API implementing the same roles
│               and workflow for real: JWT auth, employee/leave/payroll
│               data model, role-based access control.
│               See backend/README.md.
│
└── .github/workflows/   CI — builds each project independently,
                          only when files under it change.
```

Each folder has its own `README.md` with setup instructions specific to
that project — start there.

## Status

The `frontend` is a fully click-through prototype used to validate the
screens and workflows. The `backend` is a real, runnable API that
implements the same data model and role rules, but doesn't yet do
everything the spec calls for (PDF payslip generation, scheduled email
delivery, and a few other pieces are still open — see
`backend/README.md` for the honest list). Wiring the frontend to call the
backend instead of its in-memory state is the next step once the backend
is further along.

## License

Proprietary — see [`LICENSE`](./LICENSE). Internal use only.

## Hosting on the company domain (no github.io address)

The frontend can be served from Render at the root of its own domain, e.g.
`portal.kandkmedia.co.za`, instead of GitHub Pages:

1. Render → **New → Static Site** → this repository.
   - Root directory: `frontend`
   - Build command: `npm install && npm run build`
   - Publish directory: `dist`
   - Environment variable: `VITE_BASE=/`
2. Render → the static site → **Settings → Custom Domains** → add `portal.kandkmedia.co.za`,
   then add the CNAME record Render shows in the domain's DNS (Afrihost).
3. Optional: give the backend `api.kandkmedia.co.za` the same way and rebuild the
   frontend with `VITE_API_BASE_URL=https://api.kandkmedia.co.za`.
4. Once the new address works: make the repository private and turn off GitHub Pages
   (repository Settings → Pages). Render keeps deploying from a private repository.

The backend already accepts requests from `*.kandkmedia.co.za` and `*.onrender.com`.
