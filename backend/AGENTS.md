# AGENTS.md

Guidelines for implementing the DocFlow Java backend. The authoritative design is `../docs/architecture.md`; this file turns it into working rules. Keep the student-project scope and do not reintroduce deferred production mechanisms.

## Project Goals

- Implement upload, OCR, indexing, GenAI summary/classification, tagging and full-text search.
- Keep three logical layers inside the API: HTTP facade, business layer and data access. OCR, indexing, GenAI and batch run as separate applications.
- Support React/PWA through the HTTP API, with JSON DTOs and documented upload/download media types. Clients never access the database, broker or object store directly.
- PostgreSQL stores structured data, MinIO stores files, Elasticsearch provides search, and RabbitMQ connects processing stages.
- Docker Compose is the primary local/grading environment; grading needs no paid cloud access. Cloud demonstrations use manual deploy/stop workflows after successful CI.
- The user confirms a three-student team, PWA acceptance and that demonstrating a configurable daily scheduler is sufficient.
- Externalize credentials, URLs, ports, paths, limits and secrets through properties/environment variables.

## Stack and Module Layout

- Java 25 LTS, Spring Boot 4.x, Maven multi-module build and committed Maven Wrapper. Base package: `at.fhtw.docflow`. Pin versions per architecture section 5.
- `message-contracts/`: shared message envelopes/payloads, no JPA entities or business logic. Repository-root `contracts/` contains OpenAPI, event schemas and XML XSD.
- `apps/api`: authentication, documents, categories, current classification suggestions/assignments, search, processing status, result consumers and direct message publishing.
- `apps/ocr-worker`: PDFBox rasterization and Tesseract; publishes OCR results.
- `apps/indexing-worker`: Elasticsearch upserts by document ID; skip missing documents.
- `apps/genai-worker`: separate summary/classification handlers calling Gemini from Sprint 5.
- `apps/batch-worker`: long-lived Spring scheduler for XML access-log import, owning the audit schema.
- `integration-tests/`: focused cross-application tests.
- Dependencies include Spring MVC, Data JPA/Hibernate, Jakarta validation, Security/JWT, AMQP, PDFBox, MinIO SDK, Elasticsearch client, MapStruct and springdoc. Use the architecture's dependency baseline when adding libraries.
- No Flyway or mandatory Spring Batch. Use Spring `@Scheduled` for the importer.
- Share message contracts where needed, never JPA entities or a common business model. Keep adapters inside applications until actual duplication justifies a shared module.

## Architecture and Mapping

- Organize each application by feature, with layers as needed: `api`, `application`, `domain`, `persistence`, plus integration/configuration code.
- Controllers handle HTTP, DTOs and transport validation. They call application services, never repositories or external clients directly.
- Application services coordinate authorization, transactions and external calls through ports. Keep `@Transactional` here; use read-only transactions for queries and disable Open Session in View.
- Domain models use ordinary Java without Spring, JPA, Jackson or HTTP dependencies. Enforce useful invariants with simple constructors/behavior methods, such as valid state transitions and explicit category confirmation. Introduce factories, value objects or policies only for a concrete need.
- Keep HTTP DTOs, business models and JPA entities separate. Persistence adapters map models/entities; ports expose business types. Queries must not unexpectedly mutate business state.
- Use constructor injection, no field injection. Business components use interfaces where required for exchangeable implementations; keep Spring composition in configuration/adapters.
- Use shared MapStruct `@MapperConfig`, constructor injection for Spring-managed mappers and `unmappedTargetPolicy = ReportingPolicy.ERROR`. Configure the annotation processor and explicitly ignore intentionally unmapped fields with a reason.
- Do not hide business decisions in mapper hooks/expressions or bypass invariants through blind entity updates. Test custom conversions and protected fields, not every generated field copy.

## REST API Rules

- Endpoints follow architecture section 4 under `/api/v1`:
  - `POST/GET /documents`, `GET/DELETE /documents/{id}`, `PUT /documents/{id}/metadata`, `GET /documents/{id}/content` and `/documents/{id}/processing`.
  - `GET /search?q=...&categoryId=...&page=...`.
  - `GET/POST /categories`, `PUT /categories/{id}`.
  - `POST/GET /documents/{id}/classification` requests processing or reads current suggestions/status; `PUT /documents/{id}/categories` confirms assignments.
  - `POST /auth/login`, `GET /auth/me`, `POST /auth/logout`, `PUT /auth/password`.
  - Admin-only `POST/GET /admin/users`, `PUT /admin/users/{id}`, `POST /admin/users/{id}/reset-password`. No public registration.
- Provide tag management and authenticated retry/reindex operations. No classification-history/feedback endpoints.
- Use explicit PUT edit DTOs for replacement of editable representations. Required fields must be supplied; nullable fields may be cleared. IDs, ownership, processing state and audit fields are not editable. No generic PATCH/three-state field framework.
- Upload returns `202 Accepted` with document ID/status URL after storage and database commit. Publishing failures remain visible in status; retry processing for the existing document instead of creating a duplicate. Original PDF content is immutable; replacement means a new upload.
- Use conventional statuses: 200 reads/updates, 201 creation with Location where useful, 202 asynchronous work, 204 deletion, 400 invalid input, 401 unauthenticated, 403 forbidden, 404 missing/not owned, 409 conflict and 502/503 appropriate upstream failures.
- Validate DTOs with Jakarta validation and mirror documented file/page/field limits. Centralize problem responses with `@RestControllerAdvice`; never expose stack traces, SQL or internal paths.
- Keep CORS centralized. Maintain springdoc and an exported `contracts/openapi.yaml` at repository root; server-code generation is optional.

## Persistence and Schema Lifecycle

- Use JPA entities and Spring Data repositories with `spring.jpa.hibernate.ddl-auto=create` in the API and batch applications. Hibernate recreates mapped tables at startup, intentionally losing previous rows. Do not add Flyway or competing table-creation scripts.
- Provision the database and API/audit namespaces first. Restrict entity scanning/mappings to owned tables. Workers without owned JPA tables must not initialize API/audit tables. Keep one schema-owning instance per set of tables.
- Use repeatable fixtures and explicit admin bootstrap after initialization. Volumes do not prevent Hibernate resets. Pause workers and reset/reconcile stale demo messages, indexes and objects before processing resumes; retain reusable inputs separately.
- Entities: Document, Tag/DocumentTag, Category, ClassificationSuggestion, DocumentCategory, User, AccessLogEntry and BatchImport. Retain many-to-many category assignments for the additional use case.
- Store current stage statuses, request IDs and last errors on Document, together with summary/text references. Keep only current suggestions. Historical runs, feedback, processing-job, outbox and consumed-event tables are deferred.
- Every document has an owner; user-data queries are owner-scoped. Admin status does not grant access to other users' documents.
- Normalize usernames/emails consistently for unique lookup; disallow `@` in usernames. Keep USER/ADMIN roles, enabled, must-change-password and token-version fields. Never normalize passwords.
- Use lazy loading and DTO boundaries. Join entities carry approval/source data where needed. Cascade/orphan removal applies only to owned children; never delete shared categories, tags or users when removing assignments. Avoid blanket `CascadeType.ALL`.
- Store file bytes only in MinIO. Batch document references are external identifiers, not cross-schema JPA relationships.

## Messaging, Workers and Deletion

- Use durable queues, persistent messages, manual acknowledgements, bounded retries/backoff and a dead-letter queue. Messages carry document/request IDs, object keys and correlation IDs, never PDF bytes. Use explicit contract versions such as `DocumentUploaded.v1` and `OcrCompleted.v1`.
- Commit business state, then publish directly using publisher confirms. Apply this to upload and downstream requests. Record publishing failures and allow manual retry; no transactional outbox/dispatcher.
- A crash between commit and publish can leave work stuck; publisher uncertainty can cause duplicate delivery. Expose status/manual retry. Do not promise exactly-once processing or automatic stale-job recovery.
- Reuse the current request ID on broker redelivery, ignore already-applied/superseded results and prevent overlapping requests for one stage. Deterministic OCR keys and Elasticsearch upserts reduce duplicate effects; repeated AI calls remain possible and must have bounded retries/cost.
- OCR fetches from MinIO, rasterizes with PDFBox and invokes Tesseract with argument arrays, never shell strings. Bound pages/memory/time; store extracted text under a deterministic key and publish results before acknowledging input. Workers do not write document tables.
- Keep indexing, summary and classification independent. Invalid files fail visibly; transient errors retry within bounds; repeated failures reach the DLQ.
- Workers check document existence through an internal API protected by a backend-only service credential before processing/writing results. Result consumers discard missing-document results. Index by document ID; no content-version deletion protocol.
- Delete document metadata and owned rows transactionally, then attempt MinIO/Elasticsearch cleanup. Log failures with object/document IDs for manual cleanup retry. Attempt object removal if an upload's database save fails.
- No tombstones, cleanup-event workflow or periodic reconciliation service. Existence checks do not eliminate deletion races; external objects/index entries may reappear. Accept this limitation and use manual cleanup or a fresh demo environment.

## MinIO and Search

- Generate object keys server-side; supplied filenames are metadata only. Constrain access to configured buckets/prefixes.
- Search is eventually consistent and rebuildable from PostgreSQL and extracted text. Support pagination, full-text/fuzzy queries and accepted-category filters.
- Check existence and owner permissions in PostgreSQL for every returned search hit. If Elasticsearch is down, report search unavailability while normal listing remains usable. A manual reindex action is sufficient.
- Run Elasticsearch, Logstash and Kibana locally and in full cloud sessions from Sprint 4; verify log ingestion and visibility before submission.

## GenAI and Classification

- Only the GenAI worker calls Gemini through a service-agent interface. Externalize endpoint/key/model/timeouts/token limits. Retry transient failures with bounded backoff; repeated calls may incur cost. Never log keys or document text.
- Multi-label classification uses the seven initial categories in the architecture with mandatory human confirmation. Suggestions never automatically become accepted assignments.
- ClassificationSuggestion stores current suggestions separately from DocumentCategory. Users may confirm multiple labels, reject all or choose alternatives; Unclassified is a UI state.
- Validate structured output: known active category IDs, no malformed/duplicate/excessive suggestions, empty results permitted. Recheck categories at save/confirmation. Treat document text as untrusted input; it cannot authorize actions or create categories.
- Successful reclassification replaces current suggestions while preserving accepted assignments until confirmation. Separate feedback/history records and numeric confidence scores are unnecessary initially.
- Use distinct summary/classification contracts. Sprint 1 implements domain/API/persistence and clearly identified deterministic fixtures/stubs; live Gemini and the full review flow arrive in Sprint 5.

## Batch Application

- Separate long-lived application using Spring `@Scheduled`; default daily schedule 01:00 Europe/Vienna. Configure schedule, timezone, input path, filename pattern and archive/error paths.
- Scheduled invocations call the import service without restarting Spring or its persistence context. Each application restart with `ddl-auto=create` resets audit/import tables; do not start a new process for each daily run.
- Provide XSD and sample XML; disable XXE. Deduplicate by source/event ID and checksum. Commit entries/import status before archiving; preserve failed files/reasons. Deduplication lasts within the current database lifecycle.
- Own audit-schema JPA mappings, not API tables. In cloud sessions, API upload stages XML through a MinIO inbox into the worker folder; retain folder scanning/pattern/archive behavior.
- Demonstrating a configurable daily scheduler is confirmed sufficient. Exercise a near-future scheduled trigger, verify imported rows/archive outcome and restore the daily configuration. Continuous execution between sessions is not required; a manual trigger only supplements the scheduler check.

## Security and Logging

- Spring Security bearer JWT; username-or-email/password login, no public registration/social login. Use Spring JWT support, no custom cryptography; validate signature, issuer, audience and expiry. Keep the accepted 15-minute token lifetime.
- Check enabled, must-change-password and token-version state; increment token version on password change/reset/disable/logout. Logout revokes outstanding tokens globally in this simple design.
- Use DelegatingPasswordEncoder with bcrypt; reject passwords exceeding its 72-byte input limit. Temporary-password accounts may only change password or log out. Bootstrap the first admin through an explicit command/environment secrets; never commit default passwords or reset them routinely. Prevent disabling/demoting the last active admin.
- Enforce ownership server-side; use 404 for other users' resources. Rate-limit login and use identical unknown-user/wrong-password messages. Disable form/basic-auth defaults; revisit CSRF if cookies are introduced. Use HTTPS for hosted access and exact allowed origins; local Compose retains its documented HTTP endpoint.
- Use SLF4J/Logback, not System.out. Log IDs, stages, durations and failure categories, never secrets, file contents or personal data. Propagate correlation IDs through HTTP/queues.
- Provide Actuator health/info and visible failures with authenticated retry/reindex operations.

## Testing

- From `backend/`, run `./mvnw -B -ntp test` for database-free isolated tests and `./mvnw -B -ntp clean verify` for the complete suite, including disposable PostgreSQL Testcontainers integration tests (Docker required) and the enforced 80% application line-coverage gate. Reports are in `apps/api/target/surefire-reports`, `apps/api/target/failsafe-reports` and `apps/api/target/site/jacoco`. On Windows PowerShell use `./mvnw.cmd`.

- Require >70% coverage and target 80% meaningful application line coverage with JaCoCo; keep generated-code exclusions narrow.
- Test valid/invalid controller and business-component cases, authorization, validation, classification confirmation, custom mappings and XML parsing. Mock I/O, not the business behavior under test.
- MVC tests verify contracts/statuses/validation. PostgreSQL integration tests verify entity-generated schema, queries, ownership and relationship deletion without removing shared records. No migration tests.
- Use focused cross-application scenarios. Exercise actual OCR in known-PDF upload -> OCR -> search; stub OCR only for isolated tests. Stub Gemini in CI so tests need no LLM account.
- Include a basic duplicate-message case, one AI failure case and repeated XML import. Defer exhaustive crash-timing/stale-message/provider-error matrices. Bound asynchronous waits.
- Before submission, smoke-test the relevant Compose stack including ELK from Sprint 4. Expensive full-stack checks may run manually before release; separately verify live AI before demonstrations.

## Containers and Workflow

- Use multi-stage builds, small compatible runtime images, needed OCR tools only, and `.dockerignore`. Preserve required sibling modules in build contexts; exclude secrets, uploads, caches and IDE/Git metadata.
- Keep secrets outside images, run non-root where straightforward, use named volumes and health checks/startup retries. Defer specialized hardening/provenance pipelines and read-only-filesystem enforcement.
- Rebuild the whole small stack; no affected-service detection required. Automatic CI checks builds/tests; manual workflows deploy a passing main commit and stop cloud compute afterward. The session owner verifies shutdown.
- Keep `.env.example` complete and secret-free; group configuration with `@ConfigurationProperties`.
- Prefer small classes, records for immutable DTOs/contracts and clear service-boundary Optional handling. Avoid unnecessary abstractions and comments that only repeat code.
- GitFlow: feature PRs to dev, release PRs to main, another teammate reviews; merge complete tested increments only.

## Implementation Checklist

1. Validated DTOs and documented endpoints matching the current OpenAPI contract.
2. Application use case with authorization/transactions and useful domain behavior.
3. Owner-scoped queries, correct JPA relationships and schema generation with `ddl-auto=create`.
4. Constructor-injected ports/adapters for external I/O; MapStruct boundaries with protected fields.
5. Current request/status handling, bounded retries and visible/manual recovery for asynchronous work.
6. Correlation-aware logging and relevant success/validation/missing/wrong-owner tests; focused integration checks for the feature.
