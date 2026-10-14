# AGENTS.md

Guidelines for implementing the DocFlow React frontend (web UI + installable PWA). Follow these rules when changing code in this directory. The authoritative design is `../docs/architecture.md`.

## Project Goals

- Build the DocFlow web UI: login, document dashboard/list, upload, detail view, metadata/tag editing, full-text + fuzzy search, classification review, and admin screens for users and the category taxonomy.
- The same application is the Sprint 5 smartphone deliverable as an installable PWA — responsive and functional on a phone from the start; do not bolt mobile on at the end.
- The frontend uses the HTTP API at relative `/api/v1` URLs, with JSON DTOs and contract-defined file upload/download media types. Nginx and the Vite dev server proxy `/api` while preserving `/v1`. Never embed private hostnames, credentials or direct MinIO/database access.
- Keep the app usable while documents process asynchronously: upload returns `202`, then poll processing status only while work is active.

## Stack

- Node.js 24 LTS (`.node-version` committed), npm with a committed `package-lock.json`; always install with `npm ci` in CI.
- React 19 + TypeScript + Vite. TypeScript stays on the version supported by typescript-eslint (see `../docs/architecture.md` §5 — do not upgrade past the lint ecosystem's peer range).
- React Router for routing; TanStack Query for all server state, caching and polling; local React state (`useState`/`useReducer`/Context) for UI state. No additional global-state library.
- CSS Modules + a small shared stylesheet. No UI component framework without a team decision.
- `fetch` wrapped in a typed API layer — no axios required.
- Tooling: ESLint + typescript-eslint + React hooks/refresh plugins, Prettier. Lint, typecheck and format are part of done.
- Tests: Vitest + React Testing Library + jsdom for unit/component tests; Playwright for e2e (Sprint 6 integration runs against the Compose stack).
- Build/deploy: multi-stage Dockerfile (Node build -> Nginx runtime). Nginx serves the SPA with `index.html` fallback and proxies `/api`.

## Project Structure

```text
src/
  app/                  # Router, providers (QueryClient, auth), layout, error boundary
  features/
    authentication/     # Login, password change, session handling
    documents/          # Dashboard/list, upload, detail, metadata + tag editing
    search/             # Search bar, results, filters
    categories/         # Admin taxonomy management
    classification/     # Suggestion review (accept/reject/correct labels)
  shared/
    api/                # Typed fetch client, DTO types, error parsing
    components/         # Reusable presentational components
    styles/             # Global tokens/styles
```

- Keep feature code self-contained; shared code only for real reuse. Components import API access through `shared/api`, never raw `fetch` scattered in components.
- Type API responses from the backend contract (repository-root `contracts/openapi.yaml`); keep DTO types in `shared/api` and update them when the API changes.

## API and Authentication Rules

- One typed client in `shared/api` uses base URL `/api/v1`; endpoint paths such as `/documents` must not repeat `/api` or `/v1`. It handles bearer headers, request/response media types and problem-response parsing.
- Encode JSON only for JSON endpoints. Upload PDFs using the body/media type defined by OpenAPI; when using FormData, let the browser set the multipart boundary. Read document downloads as binary/Blob data, not JSON, and release temporary object URLs after use.
- Use PUT edit DTOs: `/documents/{id}/metadata`, `/categories/{id}` and `/admin/users/{id}` replace their editable representations. Supply required fields and explicit null only for nullable fields; exclude IDs, ownership, processing state and audit fields. No generic PATCH support.
- Use POST `/documents/{id}/classification` to request classification, GET on that path for current suggestions/status, and PUT `/documents/{id}/categories` to confirm assignments. Keep current suggestions separate from accepted labels; reclassification does not silently change accepted labels. No classification-run history or feedback endpoints/screens.
- The JWT access token lives **in memory only** (React state/context). Never `localStorage`/`sessionStorage`/cookies for tokens or passwords; expiry or reload means logging in again.
- Login accepts username **or** email + password. There is no registration UI — accounts are admin-created. Support the must-change-password flow: after a temporary-password login, restrict the app to password change + logout until completed.
- Treat `401` on an authenticated request as session expiry and return to login; a rejected login shows an authentication error without a redirect loop. Handle `403`/`404` according to the API ownership semantics without revealing other users' documents.
- On logout, expiry or account switching, stop polling, cancel in-flight private queries and clear the token, private React Query data and user-specific UI state. Ignore late responses from the old session so they cannot repopulate the new session's cache. Do not persist private query caches; clear local session data even when the logout request fails.
- Enforce role-based UI (admin screens visible/functional only for `ADMIN`) but remember the server is the real authorization boundary — hidden buttons are UX, not security.
- Client-side validation mirrors server limits (required fields, file type = PDF, size bound, field lengths) and shows server `400` details on the fields they belong to.
- Poll document processing status via React Query `refetchInterval` only while a stage is active and the session/view is current. Stop on terminal states, unmount or session end. Bound each automatic polling period; if processing takes unusually long, pause polling and show a prolonged-processing state with manual refresh and an API-permitted retry. Do not mark the server job failed merely because the UI stopped waiting.

## UI/UX Requirements

- Screens: login, dashboard (document list with pagination/filtering), upload, document detail (metadata, tags, summary, processing status, accepted categories), search results with category filters, classification review, admin user management, admin category management.
- Classification review is a first-class flow: show each suggested label with rationale, allow confirming multiple labels, rejecting all, or choosing alternative categories. Predictions are suggestions — never render them as already-applied. "Unclassified" is a normal state.
- Show per-stage status (OCR, indexing, summary, classification), failures and prolonged processing with refresh/retry actions where permitted. Retry processing on the existing document rather than reuploading it; disable duplicate retry submissions while a request is pending and respect backend conflict responses. Original PDF content is immutable in the MVP; intentional content replacement requires a new document upload.
- Handle empty, loading, error and offline states explicitly — no blank screens or infinite spinners.
- Mobile-first responsive layout: usable upload/list/detail/search/review on a phone viewport. Touch-friendly targets, no hover-only interactions.
- Basic accessibility: semantic HTML, labels on inputs, focus management on route change/dialogs, sufficient contrast, keyboard navigability.

## PWA Rules

- Installable PWA: Web App Manifest with `name`, `short_name`, `start_url`, `scope`, `display: standalone`, `theme_color` matching the page meta tag, and 192x192 + 512x512 icons (plus maskable). Served over HTTPS (localhost exempt).
- `vite-plugin-pwa` is the accepted way to generate/register the service worker and manifest — prefer it over a hand-maintained `sw.js` unless its behavior conflicts with a rule below. Either way, the caching contract is the same:
  - Cache only the public app shell and versioned static assets.
  - **Never** cache `/api` responses, login/auth traffic, document downloads, extracted text or any user data.
  - Upload, search, processing status and classification review require connectivity — show an explicit offline state instead of pretending they work.
- Use a controlled update flow (`registerType: 'prompt'` or equivalent): notify the user and reload deliberately so old assets never mix with a new release.
- Record the AI-assisted PWA generation work and human verification for the course requirement, and run the real-phone acceptance check: install, launch standalone, login, upload a PDF, search, confirm labels, verify offline message, logout, and an app update. Document the tested device/browser.

## Error Handling and Security

- One error boundary at the app level plus local error states for data fetching; map API problem responses to readable messages.
- Never log or store tokens/passwords; don't put secrets or private URLs in `import.meta.env` (it is bundled into public code).
- Sanitize/avoid injecting server or document-derived strings as HTML; no `dangerouslySetInnerHTML` for document content, filenames or LLM output.
- File uploads: `accept="application/pdf"`, show progress/errors, enforce the documented size limit client-side (server still enforces).

## Testing

- Component tests with React Testing Library: forms and validation messages, auth redirects, classification review interactions, status polling, offline/empty states.
- Unit/component tests mock API access and never require a running backend. Test client headers/media-type handling, errors, PUT edit payloads, session cache cleanup including late responses, and bounded polling/manual retry.
- Playwright integration e2e (Sprint 6) uses the real Compose backend and OCR: login -> upload known PDF -> processing -> search -> summary -> confirm category assignments. Stub Gemini for deterministic CI, not the complete backend. Keep waits bounded and use fixtures; these tests are separate from mocked unit/component tests.
- Run `npm run lint`, `npm run typecheck` and `npm test` before handing work off; CI runs the same plus `npm run build`.

## Code Style

- Function components and hooks; follow the rules of hooks (lint-enforced). Keep components small; extract reusable pieces into `shared/components`.
- Keep server data in React Query, not in Context or duplicated local state; invalidate queries after mutations instead of manually syncing.
- No default-export sprawl — prefer named exports matching file names.
- Sparse, useful comments only (e.g. why a polling interval or cache rule exists).
- GitFlow: feature branches -> PR to `develop` -> release PR to `main`; every PR reviewed by a teammate.

## Implementation Checklist

When adding a frontend feature, make sure it has:

1. Typed API functions + DTO types in `shared/api`, consistent with the OpenAPI contract.
2. Route/page under the right feature folder with loading, empty and error states.
3. Client-side validation mirroring server rules, with server errors surfaced on fields.
4. Correct auth behavior: bearer token attached, authenticated `401` returns to login, private cache/state cleared on session end, admin-only UI gated.
5. Responsive layout verified at phone width; offline/API-down states shown honestly.
6. Component tests for the main interactions; e2e coverage when the feature completes a user story.
