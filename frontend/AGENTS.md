# Frontend guidelines

Follow the repository [learning-first scope](../AGENTS.md) and [architecture](../docs/architecture.md). Build only what the current sprint needs, using small components and existing framework features.

## Stack and organization

- React 19, TypeScript, Vite, Node.js 24 LTS, and npm. Commit `.node-version` and `package-lock.json`; use `npm ci` in CI. Select compatible stable package versions when scaffolding, including the TypeScript/lint peer ranges.
- Use React Router, TanStack Query for server state/polling, and local React state or Context for UI/session state. Avoid duplicating server data or adding another state library.
- Use CSS Modules and a small shared stylesheet, a typed `fetch` client, ESLint, and Prettier. Add dependencies only for a demonstrated need.
- Group code by feature. `app/` for routing/providers, `features/` for screens, and `shared/` for reused API/components/styles are guidance; create folders and abstractions as needed.
- Use function components and hooks, named exports, and sparse comments explaining non-obvious decisions. Extract shared code only when it has real reuse.
- Build with Node and serve through Nginx in a separate container with SPA fallback and `/api` proxying. The Vite proxy also preserves `/api/v1`.

## API and session behavior

- One typed API client uses relative `/api/v1` URLs and types matching `../contracts/openapi.yaml`. Handle bearer headers, media types, and problem responses there; components use API functions.
- Upload through FormData fields `file`, `title`, and `description`; let the browser set the multipart boundary. Read downloads as Blobs and release object URLs. Mirror server PDF/size/field validation and display field errors.
- PUT replaces editable representations: send required fields and null for cleared nullable fields. Exclude protected IDs, ownership, processing state, and audit fields. Original PDF replacement means a new upload.
- Accounts are admin-created. Support username-or-email login and temporary-password accounts restricted to password change/logout. Show admin account/taxonomy screens only to admins; the server enforces authorization and ownership.
- Keep JWTs in memory only; reload/expiry requires login. Never persist tokens, passwords, or private query caches. An authenticated `401` ends the session; a rejected login displays an error without a redirect loop.
- On logout, expiry, or account switch, cancel private requests/polling and clear token, private queries, and user-specific state, even if logout fails. Ignore late responses from the previous session.
- Poll processing only while a stage is active and the view/session is current. Stop at terminal states, unmount, or session end. Bound automatic polling; show prolonged processing with manual refresh/retry without declaring the server job failed.
- Retry processing on the existing document, disable repeated submissions while pending, and handle backend conflicts.

## Screens and usability

- Build login/password change, paginated document list, upload, details/metadata/tags, search/category filters, classification review, and admin user/category screens in their planned sprints.
- Classification suggestions are separate from accepted assignments. Show rationale and allow multiple labels, rejection of all, or alternatives; only explicit confirmation changes accepted labels. Unclassified is valid. No history/feedback screens.
- Show per-stage status, failures, loading, empty, error, and offline states. Upload/search/review require connectivity.
- Make the same app usable on phone screens, with touch-friendly controls, semantic HTML, labeled inputs, keyboard navigation, focus management, and sufficient contrast.
- Use an app error boundary plus local request errors. Render document/LLM text as text; do not inject it as HTML. Never bundle private endpoints/secrets or log credentials.

## PWA acceptance (Sprint 5)

- Use `vite-plugin-pwa` for the manifest and service worker. Include name/short name, start URL/scope, standalone display, matching theme color, and 192/512 px plus maskable icons. Serve over HTTPS, except localhost.
- Cache only the public app shell and versioned static assets. Never cache API/auth responses, documents, extracted text, or user data. Show explicit offline messaging.
- Prompt before applying an update/reloading.
- Record AI-assisted generation and human verification. On a real phone, verify install, standalone launch, login, PDF upload, search, label confirmation, offline messaging, logout, and update. Record device/browser.

## Verification

- Use Vitest, React Testing Library, and jsdom for focused component/client tests with mocked API access. Cover forms, valid/invalid input, auth redirects/session cleanup including late responses, classification confirmation, bounded polling/retry, and empty/offline states.
- Sprint 6 Playwright scenarios use the real Compose backend/OCR with bounded waits: login -> upload known PDF -> search -> summary -> confirm labels. Stub Gemini in CI.
- Before handoff run lint, typecheck, tests, and build. Add these to CI when scaffolding the frontend.
