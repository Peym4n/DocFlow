# DocFlow — Sprint 1 implementation and usage

**Course:** SWEN3 / BIF5

**Status:** Setup modernization groups 1–6 complete; the agreed Sprint 1 feature milestone is **not complete**.

## Progress tracking

- **Setup:** Groups 1–6 are complete, including backend CI and setup documentation.
- **Verification baseline:** 31 isolated tests and 6 integration tests pass; application line coverage is 97.2%. Docker/Compose startup, API examples, health, Swagger and OpenAPI export were checked successfully.
- **Sprint 1:** Metadata CRUD is implemented; ownership, PDF upload/MinIO, tagging, classification/category foundations and pagination remain outstanding.
- **Later work:** React, authentication, messaging, OCR/search, live AI/PWA and batch processing remain scheduled for later sprints. Add frontend CI when the frontend is introduced.
- **Delivery checks:** Remote CI execution, repository access/branch protection and clean-checkout submission ZIP verification still require confirmation.

## Implemented scope

- Maven parent/aggregator at `backend/`, API module at `backend/apps/api`, committed Maven 3.10.0 Wrapper, Java 25 and Spring Boot 4.1.1.
- Feature/layer packages under `at.fhtw.docflow`; separate HTTP DTOs, plain Java business models and JPA entities.
- Document metadata create/list/get/replace/delete through use-case and persistence interfaces, constructor injection and MapStruct 1.6.3 boundary mappers.
- Jakarta validation, versioned routes, centralized problem responses and springdoc 3.1.1/OpenAPI export.
- PostgreSQL persistence with application-service transactions, `ddl-auto=create`, and Open Session in View disabled.
- Externalized database configuration, wrapper-based multi-stage Docker image, non-root runtime, Compose readiness checks, Actuator, `.env.example` and repository ignore rules.
- Isolated tests, PostgreSQL Testcontainers integration tests, JaCoCo 0.8.14 reporting/80% line-coverage gate, and GitHub Actions for PRs and pushes to `dev`/`main`.

This stores **metadata only**, not PDF bytes. There is no frontend, authentication, owner enforcement, MinIO, RabbitMQ, OCR, Elasticsearch, live AI, classification/category/tag API or batch application yet. `contentType` is validated for presence/length, not MIME syntax or actual PDF contents. Listing is unpaginated.

## Structure and boundaries

```text
backend/
  pom.xml
  mvnw / mvnw.cmd / .mvn/wrapper/
  Dockerfile
  apps/api/
    pom.xml
    src/main/java/at/fhtw/docflow/
      api/ApiExceptionHandler.java
      configuration/
      document/
        api/
        application/
        domain/
        persistence/
    src/main/resources/application.properties
    src/test/java/at/fhtw/docflow/
contracts/openapi.yaml
scripts/export-openapi.ps1
tests/http/documents.http
docker-compose.yaml
.github/workflows/backend-ci.yml
```

The HTTP controller depends on `DocumentUseCases`, implemented by `DocumentService`. The service depends on the business-facing `DocumentStore` port, implemented by `JpaDocumentStore`, which uses `DocumentRepository` and `DocumentPersistenceMapper`. `DocumentMapper` converts HTTP DTOs/business models; no JPA entities cross into the controller or business model. Transactions belong to the application service, with read-only queries and write transactions for mutations. Persistence conversion occurs within those transactions.

## Configuration and local Java execution

### Prerequisites

Docker/Compose with Linux containers is required for Compose and integration tests. Local Maven execution requires JDK 25. The Wrapper downloads Maven; no separate Maven installation is required. Initial builds require network access. The current Compose database is digest-pinned PostgreSQL 18.6; integration tests use disposable PostgreSQL 18.3 Alpine containers. Host Java 25.0.3 and Docker Engine 29.1.2 were used for verification; these patch versions are evidence, not additional project requirements.

### Environment variables

Copy repository-root `.env.example` to the ignored `.env`, then choose a local password. Do not commit real environment files.

| Variable | Example/default | Purpose |
| --- | --- | --- |
| `POSTGRES_DB` | `docflow` | Database name |
| `POSTGRES_USER` | `docflow` | Database user |
| `POSTGRES_PASSWORD` | Set your own | Required password; `.env.example` contains only a placeholder |
| `POSTGRES_PORT` | `5432` | Host database port; Compose DB remains port 5432 internally |
| `API_PORT` | `8080` | Host API port in Compose |
| `SERVER_PORT` | `8080` | Local Java HTTP port; Compose fixes the container port at 8080 |
| `SPRING_DATASOURCE_URL` | Derived from local DB variables | Optional explicit local JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | `POSTGRES_USER` | Optional explicit local database username |
| `SPRING_DATASOURCE_PASSWORD` | `POSTGRES_PASSWORD` | Optional explicit local database password |

Compose loads `.env`; Spring Boot and Maven **do not** load that file automatically. For local Java, export the same database variables in the shell or configure them in the IDE run configuration. Keep passwords out of command-line arguments and source files. `API_PORT` is only a Compose host mapping; use `SERVER_PORT` for local Java.

PostgreSQL image initialization uses database/user/password variables only on a new data directory. Editing `.env` does not rotate credentials in an existing volume. Reuse the matching credentials or explicitly prepare a different database/volume; do not remove a volume just to bypass a login failure. The PostgreSQL 18 Compose volume is distinct from the old PostgreSQL 17 volume; do not attach an older major-version data directory without a proper upgrade.

### Full Compose stack

From the repository root:

```bash
docker compose config --quiet
docker compose up --build --detach --wait
docker compose ps
docker compose logs --follow backend
```

The file is `docker-compose.yaml`. The build context is the repository root and the API Dockerfile is `backend/Dockerfile`, not under `apps/api`. To build only the image:

```bash
docker build --file backend/Dockerfile --tag docflow-api:local .
```

Ordinary shutdown:

```bash
docker compose down
```

This retains named volumes, but does **not** make metadata survive the next API startup.

### Local Java with PostgreSQL in Docker

Do not run two API instances against the same database: each startup recreates its mapped tables. Stop the Compose backend before switching to local Java. From the repository root:

```bash
docker compose stop backend
docker compose up --detach --wait db
```

In Bash, load your trusted local `.env` as shell variables, then start only the API module:

```bash
set -a
source .env
set +a
export SERVER_PORT="${API_PORT:-8080}"
cd backend
./mvnw -B -ntp -pl apps/api spring-boot:run
```

The example `.env` uses shell-compatible assignments; if you change its format, export the variables manually instead. Sourcing a file executes it, so only source your own trusted local file.

In PowerShell, enter the matching database settings without printing the password:

```powershell
$env:POSTGRES_DB = 'docflow'
$env:POSTGRES_USER = 'docflow'
$env:POSTGRES_PORT = '5432'
$env:SERVER_PORT = '8080'
$env:POSTGRES_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host 'Local PostgreSQL password' -AsSecureString)).Password
Set-Location backend
./mvnw.cmd -B -ntp -pl apps/api spring-boot:run
```

Replace these nonsecret values if your `.env` differs. The application defaults to the local host database; the Compose API instead uses `jdbc:postgresql://db:5432/...`. An IDE should launch `at.fhtw.docflow.BackendApplication` in the API module with the same environment variables.

After `verify`, the executable artifact is `backend/apps/api/target/api-0.0.1-SNAPSHOT.jar`. From `backend/`, with the environment configured:

```bash
java -jar apps/api/target/api-0.0.1-SNAPSHOT.jar
```

### Intentional schema reset

`spring.jpa.hibernate.ddl-auto=create` drops/recreates mapped database objects at every API persistence-context startup. **All existing document rows are lost on API restart**, rebuilding/redeploying the API or switching between Compose and local Java. This happens even with the persistent `postgres18_data` volume. Normal shutdown does not additionally drop tables (`create`, not `create-drop`). No Flyway or competing table-creation scripts are used.

Use disposable demonstration data and keep reusable inputs outside the database. Future object storage, messages and search indexes will need explicit demo-state reconciliation because Hibernate will not reset them. Do not perform restart/reset demonstrations on a database whose rows must be retained.

## Current REST contract

Default origin: `http://localhost:8080`; substitute your configured API port. No authentication is implemented, so use synthetic data and keep this scaffold local.

| Method | Route | Success | Missing document |
| --- | --- | --- | --- |
| POST | `/api/v1/documents` | `201`, `Location` and metadata JSON | — |
| GET | `/api/v1/documents` | `200`, raw unpaginated array | — |
| GET | `/api/v1/documents/{id}` | `200`, metadata JSON | `404` |
| PUT | `/api/v1/documents/{id}/metadata` | `200`, replaced metadata JSON | `404` |
| DELETE | `/api/v1/documents/{id}` | `204`, empty body | `404` |

| Field | Request rules |
| --- | --- |
| `filename` | Required, nonblank, maximum 255 characters |
| `contentType` | Required, nonblank, maximum 255 characters |
| `description` | Optional/nullable, maximum 255 characters |
| `id`, `createdAt` | Response only; request fields are rejected |

PUT replaces the whole editable representation. Omitting required fields is invalid; null or omitted description clears it. ID and creation time are preserved. Unknown fields are rejected for POST/PUT. POST is a setup-only JSON metadata operation returning `201`, **not** the planned multipart PDF upload returning `202`.

Errors use `application/problem+json` with `type`, `title`, `status`, `detail` and `instance`; validation errors additionally contain field messages under `errors`. Invalid input/JSON/ID returns `400`, missing documents `404`, unsupported methods `405`, stored-data conflicts `409`, and handled database failures `503`. Responses do not expose SQL, connection details or stack traces.

### Runnable examples (PowerShell)

These requests create and then delete only the example record. Use the ID returned by POST rather than assuming ID 1. Run against a disposable local demonstration database.

```powershell
$base = 'http://localhost:8080'
$create = @{ filename = 'invoice.pdf'; contentType = 'application/pdf'; description = 'Electricity bill' } | ConvertTo-Json
$document = Invoke-RestMethod -Method Post -Uri "$base/api/v1/documents" -ContentType 'application/json' -Body $create
$id = $document.id
Invoke-RestMethod -Uri "$base/api/v1/documents"
Invoke-RestMethod -Uri "$base/api/v1/documents/$id"
$replace = @{ filename = 'invoice_updated.pdf'; contentType = 'application/pdf'; description = $null } | ConvertTo-Json
Invoke-RestMethod -Method Put -Uri "$base/api/v1/documents/$id/metadata" -ContentType 'application/json' -Body $replace
Invoke-WebRequest -UseBasicParsing -Method Delete -Uri "$base/api/v1/documents/$id"
```

To inspect a validation problem with curl in Bash:

```bash
curl -i -X POST http://localhost:8080/api/v1/documents \
  -H 'Content-Type: application/json' \
  -d '{"filename":"","contentType":"application/pdf"}'
```

Expect `400` and `errors.filename`. On Windows, use the PowerShell examples above or a REST client; `tests/http/documents.http` supplies reusable requests. Listing currently has no guaranteed ordering or pagination.

## Health and OpenAPI

| Endpoint | Behavior |
| --- | --- |
| `/actuator/health` | Overall health, including PostgreSQL |
| `/actuator/health/readiness` | Readiness state plus database health; used by Compose |
| `/actuator/health/liveness` | Process liveness, independent of database availability |
| `/actuator/info` | Application description |
| `/swagger-ui/index.html` | Interactive API documentation |
| `/v3/api-docs` | OpenAPI JSON |
| `/v3/api-docs.yaml` | OpenAPI YAML |

Healthy checks return `200` with `status: UP`. Database failure makes overall/readiness health `503`; liveness can remain `200`. Health details are hidden. The old static `/api/health` endpoint is not part of the current API.

From the repository root, with the API running, export the snapshot in PowerShell:

```powershell
./scripts/export-openapi.ps1 -BaseUrl http://localhost:8080
```

The script validates expected document paths and writes UTF-8 YAML to `contracts/openapi.yaml`. Review that diff with endpoint changes; only implemented metadata operations belong in the current snapshot. The snapshot uses a relative server URL, so exports at different local ports should not alter the contract. The intended upload/content/pagination/classification API remains separate planned work.

## Tests, coverage and CI

From `backend/` in Bash:

```bash
./mvnw -B -ntp test
./mvnw -B -ntp clean verify
```

Use `./mvnw.cmd` in PowerShell. `test` runs only isolated tests without a database. `verify` also packages the executable JAR, runs Failsafe integration tests and generates/checks JaCoCo coverage. Testcontainers overrides datasource settings; it never connects these tests to the developer database. Docker must be running and able to obtain the PostgreSQL and Testcontainers helper images.

| Suite | Verified tests | Coverage focus |
| --- | --- | --- |
| Surefire isolated tests | 31 | Business use cases/validation, MockMvc routes/statuses/errors and custom/protected-field mappings |
| Failsafe integration tests | 6 | Isolated context startup, generated PostgreSQL schema, persistence/metadata replacement/deletion, transaction rollback and real HTTP contract/health/OpenAPI |

`BackendApplicationTests` belongs to Failsafe even though its name ends in `Tests`. Other integration classes use `*IT`. Container lifetimes and Spring contexts are isolated per integration class. Integration tests validate implemented metadata behavior, not absent ownership/classification relationships or external storage.

JaCoCo 0.8.14 combines unit/integration execution data and enforces a bundle/application **LINE COVEREDRATIO >= 0.80** at `verify`. Only `DocumentMapperImpl` and `DocumentPersistenceMapperImpl` generated classes are excluded; entities, DTOs, services, adapters, controllers and exception/configuration code remain included. The verified baseline is **105 covered of 108 application lines (97.2%)**; generated reports are authoritative after future changes.

Reports under `backend/apps/api/target/`:

- `surefire-reports/`: isolated-test XML/text reports.
- `failsafe-reports/`: integration-test XML/text reports and summary.
- `site/jacoco/index.html`: browsable coverage; `jacoco.xml` and `jacoco.csv` for tooling.

`clean package` runs isolated tests only; it is not the full quality gate. The image build skips test execution because CI separately runs `clean verify` before checking the image.

`.github/workflows/backend-ci.yml` runs on all PRs and pushes to `dev`/`main`, uses Temurin Java 25 and the Wrapper, verifies tests/coverage, uploads reports even after verification failure, and builds the Docker image from repository-root context after successful verification. Remote workflow execution/branch protection is a separate GitHub check; local success does not prove those settings. No cloud deployment or frontend workflow exists yet.

## Remaining Sprint 1 work

The accepted scope has not been reduced to match the scaffold:

1. **Ownership and document foundation:** owner relationship/fixtures and owner-scoped persistence; title/storage metadata. Full principal/JWT authentication remains Sprint 2.
2. **PDF upload and MinIO:** storage adapter, bounded file validation, server-generated keys, content retrieval/status, cleanup on failed database save and Compose storage service. MinIO is explicitly included in the team's Sprint 1 plan.
3. **Additional use case:** category CRUD, current classification suggestions and many-to-many accepted assignments, deterministic classifier fixtures/interface, explicit multi-label confirmation and protected shared entities. The use case is designed, but not implemented; live Gemini is Sprint 5.
4. **Tags and pagination:** separate tag relationships/API plus bounded owner-scoped listing and a documented page representation.
5. **Feature contracts/tests:** extend OpenAPI and examples for those features and PostgreSQL relationship/ownership tests. Add useful operation/correlation diagnostics without sensitive data.
6. **Submission:** another student's review, team GitHub access/checks, clean main checkout/build and source ZIP verification under course instructions. No ZIP or remote settings audit was performed by the local setup verification.

See [the readiness review](setup-architecture-review.md) for finding status and [architecture](architecture.md) for intended later sprints: React/authentication in Sprint 2, messaging in Sprint 3, OCR/search/ELK in Sprint 4, live AI/PWA in Sprint 5, and full-system integration/XML batch in Sprint 6.
