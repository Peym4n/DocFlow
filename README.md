# DocFlow

Document management coursework project for SWEN3.

Implementation progress, verification results and outstanding work are tracked in [the sprint summary](docs/sprint-summary.md).

## Prerequisites and configuration

- Docker with Docker Compose v2 or newer, running Linux containers.
- JDK 25 for local Java execution/tests. Docker builds need no host JDK or system Maven.
- The committed Maven Wrapper downloads Maven 3.10.0. First builds/tests need network access for dependencies and images.
- Default host ports: API `8080`, PostgreSQL `5432`; change them in `.env` if occupied.

From the repository root, copy `.env.example` to the ignored `.env` file and replace `POSTGRES_PASSWORD` with your own local password. Never commit `.env`.

Bash:

```bash
cp .env.example .env
```

PowerShell:

```powershell
Copy-Item .env.example .env
```

For a single-user local demonstration, explicitly set `SPRING_PROFILES_ACTIVE=demo` in `.env`. This seeds one demo owner and assigns all document requests to that owner, without authentication. Do not expose demo mode publicly. Leave the profile unset to require a trusted authenticated principal; document requests without one return `401`. Owner IDs are never accepted from request fields or headers.

Compose reads `.env` automatically. `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_PORT` and `API_PORT` configure the supplied stack. Changing database credentials in `.env` does not change credentials in an already initialized PostgreSQL volume; use the existing credentials or deliberately prepare a separate development database. See [configuration and local execution](docs/sprint-summary.md#configuration-and-local-java-execution).

## Run with Docker Compose

From the repository root (which contains `docker-compose.yaml`):

```bash
docker compose config --quiet
docker compose up --build --detach --wait
docker compose ps
docker compose logs --follow backend
```

The API image uses `backend/Dockerfile` with the **repository root** as build context. Compose waits for PostgreSQL readiness; the backend health check includes the database.

With default ports:

- Metadata API: `http://localhost:8080/api/v1/documents`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON/YAML: `/v3/api-docs` and `/v3/api-docs.yaml`
- Health: `/actuator/health`, `/actuator/health/readiness`, `/actuator/health/liveness`

Stop the stack without removing its named volume:

```bash
docker compose down
```

**Data-loss warning:** `spring.jpa.hibernate.ddl-auto=create` recreates mapped tables at **every API startup**, including restart, rebuild/redeployment and switching to local Java. Existing document rows are lost even when the PostgreSQL Docker volume survives. Shutdown alone does not drop mapped tables. Do not run two API instances against the same database. Preserve reusable demo inputs separately; this is the agreed coursework lifecycle, not durable archival storage. Removing volumes is a separate destructive action and is not required for ordinary shutdown.

## Build and test

Run from `backend/`. Bash commands:

```bash
./mvnw -B -ntp test
./mvnw -B -ntp clean verify
```

In PowerShell use `./mvnw.cmd` instead of `./mvnw`.

- `test`: runs isolated unit/MockMvc/mapping tests; no database or running Compose stack required.
- `verify`: also runs integration tests, including the context test, against disposable PostgreSQL Testcontainers databases. **Docker is required**, but no developer database credentials are needed.
- JaCoCo generates HTML/XML/CSV reports and enforces **at least 80% application line coverage** during `verify`. Only the two generated MapStruct implementations are excluded.
- Reports: `backend/apps/api/target/surefire-reports/`, `failsafe-reports/` and `site/jacoco/index.html`.
- `package` builds the API JAR and runs isolated tests; it does not run the integration suite or coverage gate. Prefer `verify` for handoff.

GitHub Actions runs Java 25 verification on pull requests and pushes to `dev`/`main`, uploads test/coverage artifacts, and checks the Docker image build.

## API contract and further instructions

| Method | Route | Success |
| --- | --- | --- |
| POST | `/api/v1/documents` | `201`, JSON metadata and `Location` |
| GET | `/api/v1/documents` | `200`, unpaginated JSON array |
| GET | `/api/v1/documents/{id}` | `200` |
| PUT | `/api/v1/documents/{id}/metadata` | `200` |
| DELETE | `/api/v1/documents/{id}` | `204` |

POST/PUT take JSON with required nonblank `filename` and `contentType`, plus optional `description` and `title`; each field is limited to 255 characters. A supplied title must be nonblank; null or omitted title defaults to the supplied filename. PUT replaces editable metadata, preserves owner/ID/creation time/original object key, and clears the description when null or omitted. Unknown fields are rejected. Errors use `application/problem+json`; field validation includes an `errors` object. POST does **not** upload a PDF or queue processing.

- [Setup, local Java execution, API examples, health, tests and OpenAPI export](docs/sprint-summary.md)
- [Architecture and intended sprint scope](docs/architecture.md)
- [Setup and architecture review](docs/setup-architecture-review.md)
- [Exported API contract](contracts/openapi.yaml)
- [Runnable HTTP requests](tests/http/documents.http)

Export the running API contract from the repository root in PowerShell:

```powershell
./scripts/export-openapi.ps1 -BaseUrl http://localhost:8080
```

Review the generated `contracts/openapi.yaml` diff before committing. No paid cloud service or LLM account is needed for the backend build and test commands above.
