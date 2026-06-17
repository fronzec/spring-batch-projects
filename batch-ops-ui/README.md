# batch-ops-ui — Read-Only Batch Operations Dashboard

A plain Svelte + Vite + TypeScript SPA that reads from the `fr-batch-service` REST API and renders plugins, job definitions, and running jobs in a minimal dashboard. This is a **walking skeleton** — read-only, no write actions, no production CORS configuration. The Vite dev proxy removes the need for any backend changes.

> **Non-production use only.** Credentials are stored in-memory and cleared on page reload. Do not use in production environments.

---

## Quick path

1. Start the backend: `fr-batch-service` must be running on `localhost:8080`
2. Install dependencies: `npm install` (inside `batch-ops-ui/`)
3. Start the dev server: `npm run dev`
4. Open `http://localhost:5173`
5. Enter **VIEWER** credentials in the login bar (e.g. `viewer` / `viewer123` on the local profile)
6. Navigate between Plugins (public), Definitions, and Running Jobs tabs

---

## Details

| Topic | Decision |
|-------|----------|
| Framework | Svelte 4 + Vite 5 + TypeScript — no SvelteKit, no SSR |
| Dev transport | Vite proxy: `/api` → `http://localhost:8080` — same-origin in dev, no CORS bean needed |
| Backend port | Defaults to `8080`; override in `vite.config.ts` → `proxy['/api'].target` |
| Wire format | `spring.jackson.property-naming-strategy=SNAKE_CASE` — all JSON keys are `snake_case` |
| DTO contract | `src/lib/dto.ts` mirrors the wire keys exactly (`job_name`, `load_status`, etc.) |
| Auth | `Basic` header built from in-memory store (`src/lib/auth.ts`) — NOT persisted |
| Endpoints | 4 GET endpoints, all read-only. No POST/PUT/PATCH/DELETE in any code path |
| Test runner | Vitest + `@testing-library/svelte` — `npm run test` |

### Snake\_case note

The backend sets `spring.jackson.property-naming-strategy=SNAKE_CASE`. This means every JSON field on the wire is snake\_case, not camelCase. The DTO interfaces in `src/lib/dto.ts` use the exact wire keys. If you see unexpected nulls, compare the field names against the raw network response.

---

## Non-production caveat

Credentials entered in the login form are:

- Held in a Svelte writable store (`src/lib/auth.ts`) — in JavaScript memory only
- **Cleared on page reload** — there is no session persistence
- **Never written** to `localStorage`, `sessionStorage`, cookies, or any other storage mechanism

This is intentional. The tool is meant for internal/non-production use where the session lifetime of the browser tab is acceptable.

---

## Scope — walking skeleton

This PR delivers the minimal read-only skeleton. The following are explicitly **out of scope** for this slice:

- Write actions (job triggering, parameter override)
- Monitoring / JobExplorer integration
- Production CORS configuration or static file hosting
- Deep-linking / URL-based navigation
- Authentication persistence or SSO integration

### Future slices

| Slice | Description |
|-------|-------------|
| Write actions | Trigger jobs, override parameters, stop executions |
| Monitoring | JobExplorer integration, step-level execution detail |
| Production hosting | CORS bean or reverse-proxy config, static `dist/` serving |
| Deep-linking | Hash or path router replacing the in-memory view switch |

---

## Scripts

| Script | Description |
|--------|-------------|
| `npm run dev` | Start Vite dev server with proxy on port 5173 |
| `npm run build` | Production build to `dist/` |
| `npm run preview` | Serve the `dist/` build locally |
| `npm run check` | TypeScript + Svelte type check |
| `npm run test` | Run Vitest tests (non-watch mode) |
