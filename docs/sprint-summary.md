# Sprint 1 progress

SWEN3 / BIF5 student project. Prioritize learning and the agreed course objectives, not production readiness. Setup and usage instructions live in [README](../README.md); the intended design lives in [architecture](architecture.md).

## Completed

- [x] Setup groups 1–6: Maven parent/Wrapper and API module, feature/layer packages, MapStruct, business/persistence boundaries, transactions, problem responses, OpenAPI, Compose, health checks, CI and coverage reporting.
- [x] Ownership foundation: minimal User relationship, owner-scoped document operations, editable titles and protected original-object keys, with two-owner PostgreSQL tests.
- [x] PDF storage: bounded upload validation, pinned source-built MinIO, original download, deferred processing status, save-failure compensation and post-delete cleanup.
- [x] Learning-first simplification: multipart-only creation with ordinary `file`, `title` and `description` fields; one owner-scoped lookup per download; concise OpenAPI annotations; focused integration tests; smaller configuration examples.

- [x] Repository-wide simplification: shared learning-first guidance, shorter frontend/design docs, historical review without a duplicate backlog, consolidated ignore rules and HTTP test helpers, and removal of the context-only test and scratch SQL.

## Public contract changes

- Document creation is a PDF upload returning `202`; the temporary JSON-only creation endpoint and its `201` response are removed.
- Multipart metadata is supplied through plain form fields, not a JSON `metadata` part.
- `DEFERRED` means no processing was queued. The public metadata-only/`NOT_UPLOADED` creation mode is removed; service fixtures remain available for isolated tests.
- The processing endpoint documents its successful `200` response. Upload-specific errors are no longer listed on read-only endpoints.
- Storage credentials use one environment naming scheme: `MINIO_ROOT_USER` and `MINIO_ROOT_PASSWORD` for the private local setup.

## Verification

Latest local Maven Wrapper `clean verify`: **60 isolated tests and 16 integration tests passed**, with no failures/errors/skips. Generated reports under `backend/apps/api/target/` remain authoritative. Repository simplification reverified this suite on 15 October 2026; the integration count decreased by one because the context-only test was removed, with all behavioral cases retained.

- JaCoCo: **287/314 application lines covered (91.4%)**; the unchanged gate requires at least 80%, excluding only generated MapStruct implementations.
- PostgreSQL and source-built MinIO integration tests use disposable containers, not the developer services.
- Previous Compose acceptance (before this documentation/test cleanup) passed: plain-form upload, byte-for-byte PDF download, metadata edit, deferred status, JSON-create rejection, deletion and readiness.
- OpenAPI was regenerated from the running container and checked for plain multipart fields, binary content and the processing `200` response schema.

Unit/controller tests cover validation, identity handling, protected fields, error responses and storage cleanup. Focused integration tests cover real PDF round trips, ownership, invalid/oversized requests, database-save failure and PostgreSQL rollback. Docker pause/unpause and hand-injected transaction-hook failure tests are intentionally deferred; basic commit boundaries and cleanup behavior remain implemented and tested without that machinery.

## Remaining Sprint 1 work

### Group 3 — Classification, categories and tags

- [ ] Implement Category, current ClassificationSuggestion and DocumentCategory assignments.
- [ ] Implement separate Tag/DocumentTag relationships and APIs.
- [ ] Seed the seven agreed taxonomy labels and add a classifier interface with deterministic fixtures/stubs.
- [ ] Require explicit multi-label confirmation; preserve accepted assignments when suggestions change.
- [ ] Test many-to-many behavior, empty suggestions, owner scoping and preservation of shared records on deletion.

### Group 4 — Pagination

- [ ] Add bounded page/size parameters, deterministic ordering and an owner-scoped page response.
- [ ] Test empty/first pages, invalid/excessive sizes and owner isolation.

### Groups 5–6 — Contracts, diagnostics and handoff

- [ ] Extend OpenAPI/examples and meaningful tests as features arrive.
- [ ] Add useful operation/request correlation diagnostics without sensitive content.
- [ ] Verify remote Actions execution, team access and required checks/reviews.
- [ ] Obtain another student's review, verify a clean main checkout and prepare/check the course source ZIP.

The additional classification use case and extra entities remain Sprint 1 requirements; completed setup/storage alone does not finish the milestone. See the [historical setup review](setup-architecture-review.md) for original finding numbers. The integration branch is `dev` locally; remote branch permissions and checks still need verification.

## Accepted limitations

- The opt-in `demo` profile selects one seeded owner; it is not authentication. Normal mode requires a trusted principal. Full login/JWT and account management remain Sprint 2.
- API startup intentionally recreates mapped tables (`ddl-auto=create`), even with persistent Docker volumes. MinIO objects survive and may require reviewed manual cleanup.
- Storage/database operations are not atomic across services. Cleanup is attempted, failures are logged, and no automatic reconciliation or exactly-once guarantee is claimed.
- Listing is unpaginated until Group 4. No tags/categories/classification API is implemented yet.
- No paid cloud or LLM account is needed for verification. Remote repository settings, hosted deployment and the submission ZIP are not certified by local tests.

## Later sprints

| Sprint | Planned work |
| --- | --- |
| 2 | React/Nginx, authentication/admin flows, principal authorization and taxonomy/review UI |
| 3 | RabbitMQ, post-commit publishing, current processing statuses and manual retry |
| 4 | OCR/indexing, Elasticsearch/Logstash/Kibana and upload-to-search demonstrations |
| 5 | Live Gemini summary/classification, complete review flow and installable PWA |
| 6 | Focused full-system integration, scheduled XML importer and final delivery |
