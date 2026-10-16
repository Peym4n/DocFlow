# AGENTS.md

Guidelines for implementing the DocFlow Java backend. Follow these rules when changing code in this directory. The authoritative design is `../docs/architecture.md`; this file turns it into working rules.

## Project Goals

- Build the DocFlow document-management backend for the SWEN3 semester project: upload, OCR, indexing, GenAI summary/classification, tagging and full-text search.
- Keep a clear three-layer architecture per the welcome slides: webservice facade (HTTP), business layer, data access layer — inside one API application, plus separate worker and batch applications.
- Support the React web/PWA frontend over HTTP/JSON only. Clients never touch the database, broker or object store directly.
- Persist structured data in PostgreSQL, files in MinIO, search projections in Elasticsearch; connect asynchronous processing through RabbitMQ.
- Keep everything runnable and gradable with `docker compose up` locally; no paid cloud dependency for grading.
- Preserve externally configured values. Database credentials, API keys, CORS origins, filesystem paths, ports, JWT secrets and upstream URLs belong in properties/environment variables, never hard-coded.

## Stack and Module Layout

- Java 25 LTS, Spring Boot 4.x, Maven multi-module build with the Maven Wrapper committed.
- Base package: `at.fhtw.docflow`.
- Modules (see `docs/architecture.md` §6):
  - `message-contracts/` — versioned queue envelope/payload types shared across applications. No JPA entities, no business logic. (The repository-root `contracts/` directory holds the OpenAPI snapshot, event schemas and the XML XSD — separate from this Java module.)
  - `adapters/messaging`, `adapters/object-storage` — shared infrastructure helpers.
  - `apps/api` — the REST application: auth, documents, categories, classification workflow, search, status, result consumers, outbox.
  - `apps/ocr-worker` — PDFBox rasterization + Tesseract OCR; publishes `OcrCompleted.v1`.
  - `apps/indexing-worker` — version-aware Elasticsearch upserts/deletes.
  - `apps/genai-worker` — Gemini summary and classification handlers (Sprint 5).
  - `apps/batch-worker` — scheduled XML access-log import; owns the `audit` schema.
  - `integration-tests/` — cross-application tests.
- Key dependencies: Spring MVC (servlet), Spring Data JPA/Hibernate, Flyway, Jakarta Bean Validation, Spring Security + JWT resource server, Spring AMQP, Spring Batch, Apache PDFBox, MinIO Java SDK, Elasticsearch Java client, Resilience4j (programmatic), MapStruct, springdoc OpenAPI. Pin versions per `docs/architecture.md` §5.
- Do not share JPA entities or a common business model across applications. Share only contracts and small adapters.

## Architecture

- Use a feature-then-layer package structure inside each app, e.g. `document/`, `classification/`, `category/`, `tag/`, `identity/`, `processing/`, `integration/`, each containing as needed:
  - `api` — REST controllers, request/response DTOs, HTTP-level mapping and validation.
  - `application` — use cases, business rules, workflow orchestration, transaction boundaries, ports (interfaces) to persistence and external services.
  - `domain` — business models and domain rules. No JPA or Jackson annotations.
  - `persistence` — JPA entities, Spring Data repositories, entity<->model mapping.
- Controllers must not call repositories, other external clients, or JPA entities directly. Controllers call application services; services call ports; adapters implement ports.
- Keep three distinct representations — HTTP DTOs, business models, JPA entities — mapped with MapStruct. Do not expose entities in APIs or DTOs in the database layer.
- Prefer constructor injection everywhere. No field injection.
- Business components are consumed through interfaces (ports/adapters), so implementations are exchangeable and mockable — this is a graded criterion.
- Keep transactions in application services with `@Transactional`; use `readOnly = true` for reads. Disable Open Session in View.

## REST API Rules

- Endpoints live under `/api/v1` per `docs/architecture.md` §4:
  - `POST/GET /api/v1/documents`, `GET/PATCH/DELETE /api/v1/documents/{id}`, `.../content`, `.../processing`.
  - `GET /api/v1/search?q=...&categoryId=...&page=...`.
  - `GET/POST /api/v1/categories`, `PATCH /api/v1/categories/{id}`.
  - `POST/GET /api/v1/documents/{id}/classification-runs`, `POST /api/v1/classification-runs/{id}/feedback`, `PUT /api/v1/documents/{id}/categories`.
  - `POST /api/v1/auth/login`, `GET /api/v1/auth/me`, `POST /api/v1/auth/logout`, `PUT /api/v1/auth/password`.
  - Admin-only `POST/GET /api/v1/admin/users`, `PATCH /api/v1/admin/users/{id}`, `.../reset-password`. No public registration endpoint.
- Conventional HTTP semantics: `GET` reads, `POST` creates/actions, `PATCH` updates, `DELETE` deletes. Upload returns `202 Accepted` with document ID/status URL once storage and commit succeed.
- Status codes: `200` reads/updates, `201` creates (with `Location` where useful), `202` accepted async work, `204` deletes, `400` validation/malformed input, `401` unauthenticated, `403` authenticated but not allowed, `404` missing or not owned, `409` conflicting state, `502`/`503` upstream failures.
- Validate all input with Jakarta annotations (`@NotBlank`, `@NotNull`, `@Size`, `@Min`, `@Max`, `@Positive`, `@Email`, nested `@Valid`) on DTOs; mirror limits (file size, page size) documented in OpenAPI.
- Centralize errors with `@RestControllerAdvice` producing consistent problem responses. Never leak stack traces, SQL, file paths or persistence details.
- Keep CORS centralized in configuration; no scattered `@CrossOrigin`.
- Keep the springdoc-generated OpenAPI contract clean and export a snapshot to the repository-root `contracts/openapi.yaml`; server-code generation is optional per the course.

## Persistence and Data Model

- JPA entities + Spring Data repositories; Flyway owns all schema. `spring.jpa.hibernate.ddl-auto=validate` always — never `update`/`create`.
- Flyway migrations in `src/main/resources/db/migration` (`V1__init_schema.sql`, then one new versioned file per change). Never edit an already-applied migration.
- Required entities per architecture §4: `Document`, `Tag`/`DocumentTag`, `Category`, `ClassificationRun`, `ClassificationPrediction`, `DocumentCategory`, `ClassificationFeedback`, `User`, `ProcessingJob`, `OutboxEvent`, `ProcessedEvent`, `AccessLogEntry`, `BatchImport`. The additional-use-case entities (classification domain) are required — not only 1:1 relations.
- Ownership is explicit: every `Document` links to its owner `User`; every user-data query is scoped by owner id (`findByIdAndOwner_Id`, `findAllByOwner_Id`, ...). Admins do not get automatic access to other users' document contents.
- Users: store `username` plus normalized `username_normalized` = `lower(trim(username))`, unique; same for email. Disallow `@` in usernames so login lookup is unambiguous. Roles `USER`/`ADMIN`, `enabled`, `must_change_password`, `token_version`.
- Add `@Version` optimistic locking to mutable aggregates. Use lazy loading; avoid bidirectional JSON recursion via DTO boundaries.
- Content versions on documents prevent late worker results from resurrecting deleted documents; check version/state before applying results.
- File bytes never go in PostgreSQL — MinIO object keys and metadata only.

## Messaging and Workers (RabbitMQ)

- Durable topic exchange, dedicated durable queues, persistent messages, manual acknowledgements, bounded retries with backoff, dead-letter queues.
- Messages carry job/document IDs, schema versions, object keys and correlation IDs — never PDF bytes. Version every contract (`DocumentUploaded.v1`, `OcrCompleted.v1`, ...).
- Delivery is at-least-once: every consumer must be idempotent. Store `ProcessedEvent` markers in the same transaction as the business update.
- Use the transactional outbox in the API: persist event + business change in one transaction; a dispatcher publishes with publisher confirms.
- OCR worker: fetch PDF from MinIO, rasterize with PDFBox, invoke Tesseract with argument arrays (never shell strings), bound pages/memory/time, write text under a deterministic versioned key, publish result before acking input. No document-table writes from workers.
- Indexing worker: version-aware Elasticsearch upserts/deletes; discard stale versions.
- Track per-stage processing state (OCR/indexing/summary/classification) with attempts and last error; recover stale jobs; invalid files fail visibly; transient failures retry; permanent failures go to the DLQ.
- No distributed transaction spans MinIO + PostgreSQL: compensate orphan uploads, reconcile unreferenced objects periodically, delete via tombstone + cleanup events only after processing settles.

## MinIO and Search

- Generate object keys server-side; the client filename is metadata only. Constrain all storage access to the configured bucket/prefix.
- Elasticsearch is eventually consistent and rebuildable from PostgreSQL + extracted text. Support pagination, fuzzy/full-text queries and category filters (accepted categories only).
- Enforce document permissions on every search hit returned. If Elasticsearch is down, report search as unavailable while normal document listing still works.
- Logstash ingests application logs into Elasticsearch; Kibana is for demonstrations. Run all ELK containers locally and in cloud sessions from Sprint 4.

## GenAI and Classification Domain

- Gemini calls live only in the genai-worker behind a service-agent interface; the API never calls the provider. Externalize base URL, key, model, timeouts, retries and token bounds. Map 4xx to actionable errors, 5xx/timeouts to upstream-unavailable; retry idempotent calls only, with bounded backoff. Never log API keys or document text.
- Classification is multi-label over the admin-maintained taxonomy (initial seven types: Invoice, Receipt, Contract, Certificate, Official correspondence, Study material, Report) with mandatory user confirmation. No confidence threshold auto-approves.
- `ClassificationPrediction` (suggestions) stays separate from `DocumentCategory` (accepted assignments). Users may accept multiple, reject all, or pick alternatives; `Unclassified` is a UI state, not a category.
- Validate structured LLM output: only known category IDs, reject malformed/duplicate/excessive predictions, allow an empty result. Treat document text as untrusted input — it must not authorize operations or create categories. Distinct prompts/contracts for summary vs classification.
- Reclassification never silently changes confirmed assignments; new predictions await another explicit confirmation.
- In Sprint 1 the domain, API, persistence and a deterministic stub classifier are implemented; live Gemini integration arrives in Sprint 5.

## Batch Application

- Separate `batch-worker` app importing external XML access logs daily (default 01:00 Europe/Vienna). Schedule, timezone, input path, filename pattern and archive/error paths are configurable.
- Provide an XSD and sample files. Disable external XML entity resolution (XXE). Deduplicate by source/event ID and file checksum; commit audit rows + import status before archiving to avoid duplicates on rerun.
- Batch owns the `audit` schema and its Flyway migrations; the API owns the document schema. Keep failed files and reasons separately.
- In hosted sessions, XML arrives via an API upload to a MinIO inbox prefix which the worker stages into its folder; the required folder-scan/pattern/archive semantics stay intact.

## Security

- Spring Security 7, stateless bearer JWT. Login accepts username **or** email + password; no registration, no social login.
- JWT: short-lived signed access token (~15 min) via Spring's JWT support — no custom crypto. Validate signature, issuer, audience, expiry. Check `enabled`, `must_change_password` and `token_version` on authenticated requests; bump `token_version` on password change/reset/disable/logout.
- Passwords: `DelegatingPasswordEncoder` with bcrypt; reject inputs over bcrypt's 72-byte limit rather than truncating. Admins set a temporary password delivered to the user, who must change it at first login (only password-change/logout permitted until then). Bootstrap the first admin via a one-time command + env secrets — never a committed default password. Prevent disabling/demoting the last active admin.
- Authorize by ownership in the service/repository layer, not by route shape. Return `404` for other users' resources to avoid revealing existence.
- Rate-limit login; identical outward message for unknown login vs wrong password. Disable form-login/basic-auth defaults; a strictly bearer API omits CSRF, revisit if cookies are ever added. HTTPS and exact allowed origins only.

## Logging and Observability

- SLF4J/Logback, never `System.out`. Log lifecycle/business events at `info`, authorization failures at `warn`, unexpected failures at `error`.
- Never log passwords, JWTs, API keys, file contents or full personal data. Log IDs, stages, durations and error categories.
- Propagate correlation IDs across HTTP and queue messages.
- Actuator health/info stays available for Compose health checks and cloud session probes. Failed-job visibility and authenticated retry/reindex endpoints exist for operations.

## Testing

- Course requires >70% coverage; we target 80% meaningful application line coverage via JaCoCo, with narrow documented exclusions.
- Write tests as features land. Valid + invalid cases for every controller action and business component.
- Unit tests (JUnit, Mockito) mock externals: business rules, validation edge cases, ownership/authorization decisions, classification workflow, mapping, import parsing.
- MVC tests for controller contracts, status codes and validation responses.
- Repository/integration tests against real PostgreSQL (Testcontainers) for queries, ownership filters and migrations; by Sprint 4 integration tests cover RabbitMQ, MinIO and Elasticsearch.
- Stub OCR, Gemini and external calls in tests — CI must be deterministic without an LLM account. Test duplicate deliveries, timeouts, malformed AI output, worker restarts and stale-result handling.

## Configuration and Code Style

- Defaults developer-friendly; real values via `.env`/environment. Keep `.env.example` complete and secret-free. Group settings with `@ConfigurationProperties` (`security.jwt.*`, `clients.gemini.*`, `storage.minio.*`, ...).
- Small single-responsibility classes; Java records for immutable DTOs/contracts; `Optional` handled at service boundaries — never return `null` from services. Sparse, useful comments for non-obvious rules (idempotency, ownership, classification semantics).
- GitFlow: feature branches -> PR to `develop` -> release PR to `main`; every PR reviewed by a different teammate; merge only complete, tested increments.

## Implementation Checklist

When adding a backend feature, make sure it has:

1. DTOs with Jakarta validation, kept separate from domain models and entities.
2. Controller endpoint with correct methods/status codes under `/api/v1`.
3. Application service with business rules, ownership checks and transaction boundaries.
4. Owner-scoped repository queries; Flyway migration if persistence changes.
5. Ports/adapters for any external service (MinIO, Elasticsearch, RabbitMQ, Gemini) — no direct calls from controllers or domain.
6. Idempotency/version handling for any queue consumer or worker result.
7. OpenAPI-visible types; correlation-aware logging for failures and integrations.
8. Tests: success, validation failure, missing resource, wrong-owner access, and async/redelivery behavior where applicable.
