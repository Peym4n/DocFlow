# DocFlow

Document management coursework project for SWEN3. See the [sprint summary](docs/sprint-summary.md) for progress and outstanding work, and [architecture](docs/architecture.md) for design decisions.

## Requirements and configuration

- Docker with Compose v2 or newer and Linux containers.
- JDK 25 for local Java execution/tests; Docker builds require no host JDK, Maven or Go installation.
- Network access for the first dependency/image downloads and MinIO source build.

From the repository root, copy `.env.example` to the ignored `.env` and choose your own `POSTGRES_PASSWORD` and `MINIO_ROOT_PASSWORD`:

```bash
cp .env.example .env
```

PowerShell equivalent: `Copy-Item .env.example .env`. If `.env` already exists, add missing settings rather than overwriting it. Never commit the real file.

Set `SPRING_PROFILES_ACTIVE=demo` explicitly for single-user local demonstrations. This seeds one owner for all document requests; it is not authentication and must not be exposed publicly. Without this profile, document requests require a trusted authenticated principal and return `401` when it is missing or unknown. Client fields/headers cannot select an owner.

The example contains the basic settings:

| Settings | Purpose/default |
| --- | --- |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Local database connection |
| `POSTGRES_PORT`, `API_PORT` | Host ports, default `5432` and `8080` |
| `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD` | Local storage credentials, used by both MinIO and the API |
| `MINIO_PORT`, `MINIO_BUCKET` | Localhost storage port `9000`, private bucket `docflow` |
| `UPLOAD_MAX_BYTES`, `UPLOAD_MAX_PAGES` | PDF limits: 10 MiB and 100 pages |

Optional overrides: `MINIO_ENDPOINT` for a different local S3 endpoint, `MINIO_TIMEOUT_MILLIS` (default `5000`), and `UPLOAD_MAX_REQUEST_BYTES` (default 11 MiB). Keep the request-envelope limit above the PDF limit if increasing file size. PostgreSQL credentials in an existing volume do not change just because `.env` changes.

## Run with Docker Compose

From the repository root:

```bash
docker compose config --quiet
docker compose up --build --detach --wait
docker compose ps
docker compose logs --follow backend
```

Compose uses repository-root build contexts: `backend/Dockerfile` for the API and `infra/minio/Dockerfile` for the pinned MinIO source build. The MinIO image includes upstream source/license material. MinIO is bound to localhost, its browser console is disabled, and its bucket is created on first upload. Compose waits for database/storage readiness.

Stop without deleting named volumes:

```bash
docker compose down
```

**Data-loss warning:** `ddl-auto=create` recreates mapped tables at every API startup, including restart/redeployment and switching to local Java. Users and document rows are lost even when the PostgreSQL volume survives. MinIO objects survive and can become orphaned. Keep reusable demo PDFs separately; after stopping activity, review leftover object keys with an S3-compatible client before removing them. Reset a disposable volume only deliberately. Do not run two API instances against the same database. No Flyway is used.

## Run Java locally

Stop the Compose API and start its dependencies from the repository root:

```bash
docker compose stop backend
docker compose up --detach --wait db minio
```

Spring Boot/Maven do not automatically load `.env`. Set matching database/storage variables in the shell or IDE. In Bash, a trusted, shell-compatible local `.env` can be loaded with:

```bash
set -a
source .env
set +a
export SERVER_PORT="${API_PORT:-8080}"
cd backend
./mvnw -B -ntp -pl apps/api spring-boot:run
```

Only source your own trusted file. In PowerShell, set the same variables through the IDE or `$env:NAME` assignments; enter passwords with `Read-Host -AsSecureString` rather than storing them in command history. Run `./mvnw.cmd -B -ntp -pl apps/api spring-boot:run` from `backend/`.

Local Java uses localhost database/storage ports; Compose supplies private service endpoints. `SERVER_PORT` controls local Java's HTTP port. After a build, the executable JAR can also be started with `java -jar apps/api/target/api-0.0.1-SNAPSHOT.jar` from `backend/`.

## Build and test

From `backend/`:

```bash
./mvnw -B -ntp test
./mvnw -B -ntp clean verify
```

Use `./mvnw.cmd` in PowerShell. The committed Wrapper downloads Maven 3.10.0.

- `test`: isolated unit, controller and mapping tests, without running services.
- `verify`: also packages the API, runs disposable PostgreSQL/source-built MinIO integration tests, and enforces at least **80% application line coverage** with JaCoCo. Docker is required; developer database/storage credentials are not.
- `package` runs isolated tests only, not the complete verification gate.
- Reports are under `backend/apps/api/target/surefire-reports/`, `failsafe-reports/` and `site/jacoco/index.html`.

GitHub Actions runs Java 25 verification for PRs and pushes to `dev`/`main`, uploads reports and checks the API image build. First integration verification also builds MinIO inside Docker.

## API usage

Default API origin: `http://localhost:8080`. Swagger UI: `/swagger-ui/index.html`.

| Method | Route | Success |
| --- | --- | --- |
| POST (multipart) | `/api/v1/documents` | `202`, document ID and processing-status URL |
| GET | `/api/v1/documents` | `200`, owner-scoped, unpaginated array |
| GET | `/api/v1/documents/{id}` | `200`, metadata |
| PUT (JSON) | `/api/v1/documents/{id}/metadata` | `200`, replaced metadata |
| DELETE | `/api/v1/documents/{id}` | `204`, metadata deletion followed by attempted object cleanup |
| GET | `/api/v1/documents/{id}/content` | `200`, original PDF attachment |
| GET | `/api/v1/documents/{id}/processing` | `200`, deferred processing status |

Upload uses ordinary multipart fields: required `file` with `application/pdf`, optional `title` and `description` (maximum 255 characters). Empty, unreadable, encrypted and excessive-page PDFs are rejected. Files use server-generated object keys; extra owner/storage form fields cannot affect those values.

```bash
curl -i http://localhost:8080/api/v1/documents \
  -F 'file=@/path/to/example.pdf;type=application/pdf' \
  -F 'title=Example' \
  -F 'description=Demo PDF'
```

Replace the file path, then use the returned ID for metadata/content requests. `Location` and `statusUrl` point to processing status. `DEFERRED` means the PDF was stored but no OCR/AI work was queued. JSON-only POST is not supported.

PUT takes required nonblank `filename`/`contentType`, and optional `title`/`description`, all limited to 255 characters. Null/omitted title defaults to the supplied filename; a supplied title must be nonblank. Null/omitted description clears it. Owner, ID, creation time and original key remain unchanged. Unknown JSON fields are rejected. Editing metadata never replaces the original PDF bytes.

Errors use `application/problem+json`; validation errors include field messages under `errors`. Missing/foreign documents return `404`, oversized uploads `413`, unsupported POST media types `415`, and handled database/storage failures `503`. Cleanup failures are logged for manual follow-up; no cross-service atomicity is claimed.

## Health and API documentation

- `/actuator/health` and `/actuator/health/readiness`: include database and storage reachability.
- `/actuator/health/liveness`: independent of external dependencies.
- `/actuator/info`: application description.
- `/v3/api-docs` and `/v3/api-docs.yaml`: generated OpenAPI; [snapshot](contracts/openapi.yaml).
- [HTTP examples](tests/http/documents.http): upload, metadata, download and deletion requests.

Export the running contract from the repository root in PowerShell and review its diff:

```powershell
./scripts/export-openapi.ps1 -BaseUrl http://localhost:8080
```
