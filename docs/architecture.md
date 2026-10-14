# DocFlow architecture

**Status:** proposed design, incorporating confirmed choices. **Research date:** 9 October 2026. This document describes the intended implementation; no application has been scaffolded, deployed or compatibility-tested yet.

## 1. Confirmed choices

- Repository: **DocFlow**; Java/Spring Boot backend and React frontend in the same repository.
- Deployment: **managed Docker container platform, no VPS**; automatic CI with manually triggered deployment/shutdown workflows and **scheduled testing/demonstrations**, not continuous hosting. **EUR 10/month maximum for the whole team**, including taxes/usage/AI. Railway is the recommended candidate, subject to a measured trial and account limits.
- Team: **three students**, explicitly reconfirmed by the user on 14 October 2026.
- Classification: **multiple labels per document, mandatory user confirmation**; live LLM integration in Sprint 5, not Sprint 1.
- Accounts: **admin-created accounts only**, username-or-email/password login, **no public registration page or endpoint**.
- Additional use case: **automatic document classification using an LLM**.
- Versions: latest LTS when available; otherwise latest stable compatible releases, excluding previews.
- Accepted defaults: owner-only document access, admin-maintained seven-label taxonomy, Gemini provider and Railway scheduled sessions with one hosting administrator.
- Mobile deliverable: **React PWA**, confirmed by the user on 14 October 2026 as satisfying the smartphone-app requirement. No separate native/Capacitor project.
- Infrastructure: pinned MinIO source build accepted; **Elasticsearch, Logstash and Kibana in containers both locally and during cloud sessions**. No continuous cloud operation.
- Database schema: **`spring.jpa.hibernate.ddl-auto=create`**, following the lecturer recommendation and team decision. Hibernate recreates its mapped tables at application startup; their previous data is discarded. No Flyway migrations are planned.

Recommend a layered REST application with separate OCR, indexing and GenAI worker applications, plus a scheduled batch application. PostgreSQL owns structured data, MinIO stores files, Elasticsearch provides search, and RabbitMQ connects asynchronous processing. The course diagram explicitly specifies a monorepo with subprojects.

### Student-project scope

Docker Compose is the primary development and grading environment. Keep the required application layers, workers, infrastructure and functional deliverables, but use straightforward implementations that the whole team can explain. Cloud demonstrations remain planned within the budget, using manual workflow triggers. Defer transactional outbox processing, distributed deletion orchestration, classification history, generic PATCH support, shared adapter frameworks and advanced hardening until a demonstrated need justifies them. The accepted reliability limitations are described below.

## 2. Course requirements and evidence

| Course requirement | Design response |
| --- | --- |
| JDK LTS >=25, Spring Boot >=3.5/4 | Java 25 LTS and Spring Boot 4.1.1 |
| REST, PostgreSQL ORM, repository pattern | Spring MVC, Spring Data JPA, Hibernate-generated tables (`ddl-auto=create`) |
| Separate business and persistence entities; mapping framework | HTTP DTOs, business models, JPA entities, MapStruct |
| Business components through interfaces; DI and validation | Application services, ports/adapters, constructor injection, Jakarta validation |
| Upload, edit, delete, metadata, tags | Document API and React dashboard/detail views |
| Full-text and fuzzy search | Elasticsearch projection and search service agent |
| RabbitMQ and separate worker applications | OCR, indexing and GenAI applications |
| PDF storage/OCR | MinIO in Compose from Sprint 1; PDFBox rasterization and Tesseract |
| Generated summary persisted in PostgreSQL | Worker result consumed and saved by API |
| Additional entities, not only 1:1 | Categories, current suggestions and many-to-many document-category assignments |
| Separate webserver container, localhost port 80 | Nginx serving React and proxying `/api` |
| Smartphone app in Sprint 5 | React PWA; user confirms this satisfies the requirement |
| Sprint 4 ELK in containers | Elasticsearch, Logstash and Kibana locally and in cloud sessions |
| Separate daily XML batch application | Access-log import, database persistence, configurable paths/schedule, archiving |
| Docker Compose throughout | Reproducible local/grading environment |
| Coverage >70%, mocked unit tests, integration tests | Proposed 80% application line-coverage gate plus critical-path tests |
| GitFlow, PRs, Kanban, CI/CD, documentation | GitHub branches/Actions/Projects and design/test documentation |

The rubric assigns 20 points each to functionality, non-functional requirements, architecture, development workflow and code-review knowledge. Containerized deployment alone is 10 points. Document decisions, diagrams, testing, lessons learned and tracked work. Every member should understand the complete system.

### Requirement details and confirmations

- The course architecture diagram includes a mobile client and a Docker Compose monorepo; its omission of Kibana/Logstash does not override the later sprint instructions.
- Sprint 1 includes the additional use case and extra entities. Implement its domain/API/persistence foundation then; the team confirms live LLM classification comes later, in Sprint 5.
- Sprint 4 requires MinIO, OCR and ELK in containers. ELK means Elasticsearch, Logstash and Kibana; all three run locally and in scheduled cloud sessions. The indexing worker is explicitly separate even though the course diagram associates indexing with GenAI.
- Sprint 5 requires a smartphone app generated with AI coding tools, with "smartphone app is available and working" as a must-have. The user confirms that an installable PWA satisfies this requirement; demonstrate it on a real phone.
- Sprint 6 requires a separate daily scheduled XML importer with configurable schedule/folders/patterns and archiving. The user confirms that demonstrating a configurable daily scheduler is sufficient; unattended daily operation between demonstration sessions is not required.
- The grading matrix scores the web frontend, core use cases and the additional use case; it has no dedicated mobile/PWA/native score. This does not remove the Sprint 5 must-have.

The course's use-case suggestions endorse LLM classification with a Document/DocumentType many-to-many relationship. The user confirms a three-student team for this project; the introductory material's two-student example does not change that decision.

## 3. Component architecture

```mermaid
flowchart LR
    UI[React web app] --> API[Spring Boot API]
    Mobile[Installed React PWA] --> API
    API --> DB[(PostgreSQL)]
    API --> Store[(MinIO)]
    API --> Search[(Elasticsearch)]
    API <--> MQ[RabbitMQ]
    MQ <--> OCR[OCR worker]
    MQ <--> Index[Indexing worker]
    MQ <--> AI[GenAI worker]
    OCR <--> Store
    Index --> Store
    Index --> Search
    AI --> Store
    AI --> Gemini[Gemini API]
    Batch[Scheduled XML importer] --> DB
    Input[Input folder] --> Batch
    Logs[Application logs] --> Logstash[Logstash]
    Logstash --> Search
    Kibana[Kibana] --> Search
```

| Application | Responsibility and ownership |
| --- | --- |
| API | Authentication, document/category/classification workflows, search, status, result consumers and direct message publishing; owns document/classification tables |
| OCR worker | Fetch PDF, render pages, recognize text, save text and publish result; no document-table writes |
| Indexing worker | Upsert Elasticsearch projections by document ID; skip missing documents |
| GenAI worker | Separate summary/classification handlers, provider calls and structured-output validation; publish results, no document-table writes |
| Batch application | External XML access-log import; owns an `audit` schema in the same PostgreSQL instance |
| React/mobile clients | Public API consumers; no direct database, broker or private-store access |

One PostgreSQL instance is sufficient. Keep table/schema ownership explicit. Share message contracts where needed. Keep adapters within their applications until real duplication justifies extraction; do not share JPA entities or a common business model across applications.

### Internal layering

`Controller -> application/business service -> repository/service-agent interface -> adapter`

This implements the welcome slide's three layers: Webservice Facade (`api`), Business Layer (`application` plus `domain`), and Data Access Layer (`persistence` and external adapters). These packages are not four separate architectural tiers.

- Controllers handle HTTP, DTOs and transport validation.
- Application services coordinate authorization, workflows, transactions and external calls. A few focused business-model methods enforce local business invariants; application services invoke that behavior rather than duplicating it.
- Persistence adapters map business models to/from JPA entities. Neither controllers nor business components use JPA entities directly.
- External service agents hide MinIO, Elasticsearch, RabbitMQ and Gemini behind interfaces.
- Use MapStruct and constructor injection. Translate adapter errors into layer-specific exceptions and consistent HTTP problem responses.

### Domain behavior and dependencies

Keep business models independent of Spring, JPA and HTTP DTOs. Use constructor injection, repository/service-agent interfaces and ordinary configuration for wiring. Controllers handle transport and delegate use cases to application services; application services coordinate authorization, transactions and external calls.

Keep useful rules close to the data: validate category names, prevent invalid processing-state changes and require explicit user confirmation before suggested labels become accepted assignments. Simple constructors and behavior methods are sufficient. Introduce value objects, factories, policies or specifications only when an actual rule benefits from them. Avoid a separate framework for domain modeling. Queries should not unexpectedly mutate business state.

Test important business rules without starting Spring or a database. Mock I/O rather than the rule being tested, and inject changing inputs such as time only where tests need deterministic control.

### Mapping policy

Use a shared MapStruct `@MapperConfig` for Spring-managed boundary mappers with constructor injection and `unmappedTargetPolicy = ReportingPolicy.ERROR`. Configure both the library and annotation processor. Explicitly ignore deliberately unmapped target fields with a reason; new target fields must not silently disappear. Keep business decisions in domain behavior, not mapper expressions, hooks or decorators.

Use explicit edit DTOs with PUT for complete replacement of editable metadata, category details and account settings. Document which fields belong to each edit representation: required fields must be supplied, nullable fields may be explicitly cleared. IDs, ownership, processing state and audit fields are not editable. Apply validated changes through application/business behavior; do not blindly map request fields onto a JPA entity. No generic PATCH or three-state field-presence framework is planned.

Test custom conversions, required/null values and protected fields. Straightforward generated field copies do not need repetitive tests that only mirror the generator.

### Database schema lifecycle

Use the following setting for the API and the batch application that own JPA-managed tables:

```properties
spring.jpa.hibernate.ddl-auto=create
```

Hibernate generates tables, constraints and sequences from the entity mappings, dropping and recreating the mapped database objects whenever the application's persistence context starts. Existing rows in those tables are lost, including on application restart or redeployment. This is intentional for the coursework environment and applies to local/grading and scheduled cloud sessions. `create` does not additionally drop the tables on normal shutdown, unlike `create-drop`. PostgreSQL and the target database must already be available.

Do not use Flyway or a competing table-creation script for these same tables. Provision the API and `audit` namespaces explicitly before Hibernate needs them; keep each application's entity scanning and schema mappings restricted to its owned tables. Workers without JPA-owned tables must not initialize the API or audit tables. Use a long-lived batch application with Spring scheduling; no Spring Batch framework metadata or job repository is needed for the initial importer.

Use repeatable fixtures and the explicit admin bootstrap command after schema creation to prepare demonstrations. Resetting API tables loses users, document metadata, current classification suggestions, assignments and processing status; resetting batch tables loses imported access events and import markers. Idempotency guarantees apply while those records exist, not across a deliberate database reset.

MinIO objects, RabbitMQ messages, Elasticsearch indexes and archived XML files are not reset by Hibernate. The reset runbook must pause consumers and explicitly reconcile or reset the relevant demonstration state before processing resumes, so stale jobs and search hits cannot refer to deleted records. Preserve reusable input fixtures separately and restage XML when a fresh import is needed. Named volumes preserve storage between container runs but do not override `ddl-auto=create`.

### Persistence relationships and deletion

Keep relationship ownership explicit and use join entities when a relationship carries data, such as who approved a category. DocumentTag, DocumentCategory and ClassificationSuggestion reference their document and a shared tag/category. Removing a join row or suggestion must never delete a shared category, tag, user or document. Use cascade/orphan removal only for genuinely owned children; avoid blanket `CascadeType.ALL`.

Deleting a document removes its owned rows and metadata in a database transaction, then attempts removal of its MinIO objects and Elasticsearch entry. Log failed external cleanup for a manual retry using the recorded object/document IDs. No tombstone table, cleanup-event workflow or scheduled reconciliation service is required. Search results must be checked against existing, authorized documents in PostgreSQL so a stale search entry is not returned.

Workers check document existence through a small internal API protected by a backend-only service credential before processing/writing results; API result consumers discard results for missing documents. This reduces stale work but does not eliminate a delete/worker race: a worker can still recreate an external object or index entry after checking. Accept this limitation for the student project and use manual cleanup or a fresh demo environment when needed. Do not claim atomic deletion across services.

Test assignment removal, document deletion and preservation of shared categories/users against PostgreSQL. Keep batch audit document references as external identifiers rather than cross-schema JPA relationships, because API and audit tables are recreated independently.

### Processing flow

1. Authenticate and validate the PDF. Store it in MinIO and commit document metadata plus initial processing status to PostgreSQL. If the database save fails, attempt to remove the uploaded object and log cleanup failures.
2. After the transaction commits, publish `DocumentUploaded.v1` directly to RabbitMQ using publisher confirms. Return `202 Accepted` with the document ID/status URL for the saved document. If publishing fails, record a visible failure and allow the user to retry processing; do not create another document on retry.
3. OCR fetches the original PDF, rasterizes it with PDFBox, runs Tesseract with limits/timeouts, stores extracted text under a deterministic key and publishes its result before acknowledging the input.
4. The API saves the OCR result and publishes separate indexing, summary and classification requests after commit. Track each stage independently so an AI failure does not prevent search. Apply the same visible-failure and manual-retry approach to failed downstream publishing.
5. The indexing worker writes by document ID. The GenAI worker returns a summary and current category suggestions through separate handlers. API consumers save results; React polls while processing is active.

Use durable queues, persistent messages, manual acknowledgements, bounded retries and a dead-letter queue for repeatedly failing work. Messages contain document/request IDs, object keys and correlation IDs rather than PDF bytes. Store only current stage status, current request ID and last error on the document; reuse that request ID for broker retries and ignore already-applied or superseded results. This avoids separate processing-job and consumed-event tables. Prevent overlapping user requests for the same stage.

There is no transactional outbox: a crash between saving database state and publishing can leave processing stuck, and publisher uncertainty can cause duplicate delivery. Expose processing status and a manual retry action for failed or stuck stages. Deterministic OCR object keys and Elasticsearch upserts reduce duplicate effects, but repeated AI calls remain possible; bound retries and cost. Do not claim exactly-once execution or automatic crash recovery.

Search is eventually consistent. Support pagination, fuzzy/full-text queries and category filters, and verify document existence/ownership before returning hits. If Elasticsearch is unavailable, report that search is unavailable while ordinary document listing remains usable. A manual reindex action is sufficient for demonstrations. Original PDFs are immutable in the MVP; users upload a new document to replace content.

## 4. LLM classification domain

Classification must be a distinct workflow beyond the already-required summaries and tags. **Confirmed:** controlled multi-label categories with mandatory human acceptance/correction. The supplied course suggestions explicitly propose LLM classification with a many-to-many Document/DocumentType relationship. `Category` in this design implements that DocumentType concept; it is separate from free-form tags.

| Entity | Relationship / relevant fields |
| --- | --- |
| Document | Owner, title, original/text keys, summary, timestamps; current stage statuses/request IDs and last errors |
| Tag / DocumentTag | Required free-form tagging; many-to-many |
| Category | Name, description, active flag |
| ClassificationSuggestion | Current suggestions per document; category and short rationale; replaced on successful reclassification |
| DocumentCategory | Accepted many-to-many assignments; source, approving user |
| User | ID, normalized unique username/email, password hash, USER/ADMIN role, enabled flag, must-change-password flag and token version |
| AccessLogEntry / BatchImport | External access event and imported-file checksum/status |

Workflow: upload -> OCR -> classification -> **awaiting user review** -> user selects/confirms labels -> persist accepted assignments -> update search projection. Suggestions remain separate from accepted assignments. Keep only the current suggestion set; historical runs, model-version history and separate feedback records are deferred. No confidence threshold can auto-approve labels. Users may reject all suggestions, choose existing alternatives, or confirm multiple labels. Category filters use accepted labels only. Reclassification leaves previously confirmed assignments unchanged until another explicit confirmation. Owners review their own documents; account administration alone does not grant access to other users' document contents. Admins maintain category definitions.

### Suggested initial document types

| Type | Description/examples |
| --- | --- |
| Invoice | Amount requested for goods/services, tuition invoice |
| Receipt | Proof of payment or purchase |
| Contract | Rental, employment or service agreement |
| Certificate | Enrollment, qualification, attendance or insurance certificate |
| Official correspondence | Decisions, notices and formal letters from institutions |
| Study material | Lecture slides, assignments and study notes |
| Report | Project, technical or inspection report |

These seven labels are the accepted initial taxonomy, editable by the administrator. They can overlap: a formal tuition invoice may be both Invoice and Official correspondence. Keep definitions in the taxonomy, allow zero accepted labels (Unclassified is a UI state), and use ordinary tags for topics such as housing or employment. Start with English/German PDFs.


Allow only known active category IDs, validate structured output, reject malformed/duplicate/excessive suggestions and permit an unclassified result. Recheck categories when saving suggestions and confirming assignments; a category may have been deactivated while processing. Numeric model-confidence scores are unnecessary for the initial review UI. Treat document text as untrusted data; it must not authorize operations or create categories. Use distinct prompts/contracts for summary and classification.

Sprint 1 includes category CRUD, assignments, additional entity relationships, mappings, business rules, API and a classifier interface. Use deterministic fixtures in tests and clearly identify unimplemented AI behavior. Live classification, the GenAI worker and the complete review workflow arrive in Sprint 5, as confirmed by the team. Do not require an LLM account for Sprint 1.

### Proposed API

- `POST/GET /api/v1/documents`; `GET/DELETE /api/v1/documents/{id}`; `PUT /api/v1/documents/{id}/metadata`.
- `GET /api/v1/documents/{id}/content` and `/processing`.
- `GET /api/v1/search?q=...&categoryId=...&page=...`.
- `GET/POST /api/v1/categories`; `PUT /api/v1/categories/{id}`.
- `POST /api/v1/documents/{id}/classification` requests classification; `GET /api/v1/documents/{id}/classification` returns current suggestions/status.
- `PUT /api/v1/documents/{id}/categories`, tag-management endpoints and authenticated retries.
- `POST /api/v1/auth/login` with `{login, password}`; `GET /api/v1/auth/me`; `POST /api/v1/auth/logout`; `PUT /api/v1/auth/password`.
- Admin-only `POST/GET /api/v1/admin/users`, `PUT /api/v1/admin/users/{id}` and `POST /api/v1/admin/users/{id}/reset-password`. No registration endpoint.

Document permissions, pagination, upload limits, concurrency conflicts and error responses in OpenAPI. Keep an exported OpenAPI snapshot and example `.http` requests. The course explicitly makes server-code generation optional.

## 5. Version baseline

Prefer LTS runtimes. Most frontend libraries and Spring's community releases do not offer an equivalent LTS designation: select maintained stable versions. Preserve Spring Boot's tested dependency set instead of upgrading each transitive dependency independently. These versions are verified published releases, not a successfully tested application combination. Pin exact build dependencies, lockfiles and container digests during scaffolding.

### Java

| Component | Proposed version | Notes |
| --- | --- | --- |
| Eclipse Temurin JDK | **25.0.4.1+1 (Java 25 LTS)** | Latest LTS baseline |
| Maven + Wrapper | **3.10.0** | Stable; Maven 4 RC excluded |
| Spring Boot | **4.1.1** | Parent/BOM; 4.2 milestones excluded |
| Spring Framework / MVC | **7.0.9** | Boot-managed |
| Spring Data train | **2026.0.1** | Boot-managed JPA repositories |
| Hibernate ORM / Validator | **7.4.5.Final / 9.1.3.Final** | Persistence / Jakarta validation |
| Spring Security | **7.1.1** | Boot-managed |
| Spring AMQP | **4.1.1** | RabbitMQ integration |
| PostgreSQL JDBC | **42.7.13** | Boot-managed |
| Jackson BOM / Logback | **3.1.5 / 1.5.38** | JSON / logging; Boot-managed |
| MapStruct + processor | **1.6.3** | Stable; 1.7 beta excluded |
| springdoc OpenAPI WebMVC UI | **3.1.1** | Boot 4 generation |
| Apache PDFBox | **3.0.8** | PDF parsing and rasterization |
| MinIO Java SDK | **9.0.3** | Verify chosen server/client combination |
| Elasticsearch Java client | **9.4.5** | Boot-managed; newer 9.x server compatibility, avoid newer-only APIs |
| Resilience4j core retry/circuit-breaker | **2.4.0** | Programmatic use; do not assume Boot 3 starter compatibility |
| Gemini adapter | Spring RestClient | Framework-managed; no extra AI SDK needed |
| Gemini model | **gemini-3.8-flash**, proposed | Stable Flash; configurable, account availability/cost to verify |

Use Boot starters for MVC, JPA, validation, security, AMQP, actuator and testing as appropriate. Use Spring scheduling in the separate batch application; add Spring Batch only if the importer later needs its job-management features. Spring AI is teaching material but not an explicit framework requirement in the supplied brief; direct HTTP keeps the service agent small. Revisit after reading the Spring AI handout.

### Frontend, mobile and tests

| Component | Proposed version |
| --- | --- |
| Node.js | **24.21.0 LTS**; Node 26 is not yet LTS on the research date |
| React / React DOM / corresponding type packages | **19.3.0** |
| TypeScript | **6.0.3**, compatibility exception |
| Vite / React plugin | **8.3.4 / 6.1.2** |
| React Router DOM | **7.18.4** |
| TanStack React Query | **5.104.1** |
| ESLint / typescript-eslint | **10.12.0 / 8.71.1** |
| Hooks / Refresh lint plugins | **7.1.1 / 0.5.7** |
| Prettier | **3.9.9** |
| Vitest / matching V8 coverage package | **5.0.3** |
| React Testing Library / jsdom | **16.3.3 / 30.1.2** |
| Playwright | **1.64.0** |
| JUnit Jupiter / Mockito | **6.0.3 / 5.23.0**, Boot-managed |
| Testcontainers | **2.0.5**, Boot-managed |
| JaCoCo Maven plugin | **0.8.15** |
| Maven compiler / Surefire / Failsafe | **3.15.0 / 3.5.6 / 3.5.6** |
| vite-plugin-pwa | **1.3.0** | Generates manifest + service worker (Workbox); 2.0.0 published within days of the research date, reassess at scaffolding |

TypeScript **7.0.2** is the newest stable observed, but typescript-eslint 8.71.1 declares `>=4.8.4 <6.1.0`. Use 6.0.3 until the lint ecosystem supports 7. This is not an LTS claim. Use npm and a committed lockfile with `npm ci`; record the bundled npm version when scaffolding the selected Node build. CSS Modules, fetch and local React state are adequate; React Query handles server state and polling. No additional UI framework or global state library is necessary initially.

### Infrastructure

| Component | Proposed version / policy |
| --- | --- |
| PostgreSQL | **18.6** in the local and hosted PostgreSQL containers |
| RabbitMQ | **4.3.6**; reassess community support before retakes |
| Elasticsearch / Kibana / Logstash | **9.5.5**, matched server stack; verify container artifacts during build |
| Nginx | **1.30.5 stable branch** |
| Tesseract | **5.5.3**, pinned English/German language data |
| MinIO community server | **RELEASE.2025-10-15T17-29-55Z**, source build accepted by the team |
| Docker Engine / Compose | **29.9.0 / 5.6.0**, stable tooling baseline |
| Docker Desktop | Supported workstation release bundling suitable Engine/Compose; no application dependency on a specific Desktop patch |

PDFBox replaces Ghostscript for rasterization; invoke Tesseract with argument arrays, not shell-assembled commands. Bound page count, memory, temporary files and process time; begin with one OCR job at a time. No Tess4J dependency is needed in this approach.

**MinIO exception:** its official community repository is archived and its latest listed release is source-only. Do not assume an available maintained `minio/minio:latest` image. Build the pinned source release, preserve its notices, and keep the course instance private. The team has accepted this source-build approach; pin the build toolchain and resulting image digest during implementation. [Repository](https://github.com/minio/minio), [release/build instructions](https://github.com/minio/minio/releases/tag/RELEASE.2025-10-15T17-29-55Z).

### Version evidence

- [Temurin releases](https://adoptium.net/temurin/releases/), [Node LTS schedule](https://github.com/nodejs/Release), [Spring Boot requirements](https://docs.spring.io/spring-boot/system-requirements.html).
- Exact managed Java versions: [Spring Boot 4.1.1 BOM](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.1/spring-boot-dependencies-4.1.1.pom). Other Java versions: publisher artifact metadata under [Maven Central](https://repo.maven.apache.org/maven2/), with milestone/RC releases excluded.
- [MapStruct](https://mapstruct.org/documentation/reference-guide/), [springdoc](https://springdoc.org/), [RabbitMQ](https://www.rabbitmq.com/release-information), [PostgreSQL](https://www.postgresql.org/docs/release/), [Elastic releases](https://www.elastic.co/docs/release-notes), [Elastic client compatibility](https://www.elastic.co/docs/reference/elasticsearch/clients/java).
- [Nginx changes](https://nginx.org/en/CHANGES-1.30), [Tesseract releases](https://github.com/tesseract-ocr/tesseract/releases), [Gemini models](https://ai.google.dev/gemini-api/docs/models), [Docker Engine](https://github.com/moby/moby/releases), [Compose](https://github.com/docker/compose/releases).
- npm versions and engine/peer requirements were checked directly at `https://registry.npmjs.org/<package>/latest`, e.g. [React](https://registry.npmjs.org/react/latest), [Vite](https://registry.npmjs.org/vite/latest), [typescript-eslint](https://registry.npmjs.org/typescript-eslint/latest). These endpoints move; the table records the research-date snapshot.

## 6. Recommended directory hierarchy

```text
DocFlow/
  README.md
  LICENSE
  .gitignore
  .env.example
  docker-compose.yml               # Complete local/grading environment
  docker-compose.dev.yml           # Optional developer overrides
  deployment/                      # Platform service configs and start/stop runbook
    railway/                       # One configuration per deployed service
  .github/
    workflows/ci.yml
    workflows/release.yml
    dependabot.yml
  backend/
    pom.xml                        # Parent/aggregator and central versions
    mvnw
    mvnw.cmd
    .mvn/wrapper/
    message-contracts/             # Queue envelopes/payloads, no JPA entities
    apps/
      api/
        pom.xml
        Dockerfile
        src/main/java/at/fhtw/docflow/
          document/
            api/                   # Controllers, DTOs, HTTP mapping
            application/           # Use cases and ports
            domain/                # Business models and rules
            persistence/           # JPA entities, repositories, mapping
          classification/          # Same feature/layer pattern
          category/
          tag/
          identity/
          processing/              # Current stage status, result handlers and retries
          integration/             # Search/storage service agents
          configuration/
        src/main/resources/
          application.yml
        src/test/
      ocr-worker/
        pom.xml
        Dockerfile
        src/main/
        src/test/
      indexing-worker/
      genai-worker/                # Summary and classification handlers
      batch-worker/                # Long-lived scheduler and audit-schema JPA mappings
    integration-tests/
  frontend/
    package.json
    package-lock.json
    .node-version
    vite.config.ts
    tsconfig.json
    Dockerfile                     # Node build -> Nginx runtime
    public/
    src/
      app/                         # Router, providers, layout
      features/
        documents/
        search/
        categories/
        classification/
        authentication/
      shared/
        api/
        components/
        styles/
    tests/
    public/icons/                  # Install icons; manifest + service worker generated by vite-plugin-pwa
  infra/
    nginx/default.conf
    rabbitmq/definitions.json
    minio/Dockerfile
    elasticsearch/
    logstash/                      # Pipeline and bounded buffering config
    kibana/                        # Config and exported dashboards
  contracts/
    openapi.yaml
    events/
    access-log.xsd
  tests/
    e2e/
    fixtures/documents/
    fixtures/access-logs/
    http/
  scripts/                         # ZIP generation, smoke tests, reindexing
  docs/
    architecture.md
    adr/
    testing.md
    deployment.md
    lessons-learned.md
```

This is the intended tree, not a set of created files. Maven builds Java and npm builds React; a monorepo needs neither Nx/Turborepo nor one package manager for both. Add shared modules only when real reuse exists. Each Java application has the usual Maven source/test tree.

Use repository-root Docker build contexts when Java services need sibling modules. For this small project, rebuild the complete stack; affected-service detection is unnecessary. Keep course downloads, secrets, uploaded files, build outputs and IDE files outside Git.

### Container build and runtime standards

Use multi-stage application builds, a `.dockerignore`, dependency caching where straightforward, and small compatible runtime images. Keep only the tools OCR needs in its runtime. Pin dependencies and keep runtime secrets outside images. Run as non-root where supported without complicated workarounds, and keep persistent data in named volumes. Health checks, startup retries and ordinary image/build smoke checks are sufficient initially.

Defer read-only filesystem enforcement, provenance/SBOM automation, specialized hardening pipelines and mandatory image-scanning gates. Keep base images reasonably current and do not expose the Docker socket or add unnecessary privileges.

## 7. Managed Docker deployment and budget

### Local/grading environment

Docker Compose is the primary environment for development, integration and grading. Cloud deployment supports booked demonstrations; it is not needed to build or grade the project.

`docker compose build` and `docker compose up` must start the full required stack. Nginx publishes port 80 for `http://localhost/`, serving React and proxying `/api`. Expose the API on host 8080 for the Sprint 5 check; document the discrepancy with the diagram's 8081.

Use named volumes for PostgreSQL, MinIO, RabbitMQ and Elasticsearch; health checks and startup retries; configurable XML input/archive mounts. Developer profiles may save resources, but the final grading instructions must start all required services. From Sprint 4, include Elasticsearch, Logstash and Kibana as separate services in the default local/grading stack and in cloud sessions.

### Platform comparison (9 October 2026)

The team confirmed that cloud availability is needed only for scheduled testing and demonstrations. No VPS is wanted. Prices below are advertised USD prices; tax, conversion and external AI are additional unless explicitly included. None of these offers establishes an always-on, complete-stack deployment below EUR 10.

| Platform | Relevant capabilities and price | Assessment |
| --- | --- | --- |
| **Railway Hobby** | Docker services, private networking and persistent volumes. $5 monthly minimum includes $5 usage; RAM $10/GB-month, CPU $20/vCPU-month, volumes $0.15/GB-month, egress $0.05/GB. | **Recommended for short scheduled sessions**, with measured usage and spending controls. $5 is not a flat price for unlimited containers. |
| **Northflank** | Custom Dockerfiles, services, databases and cron. Free Sandbox includes two services, one database and two cron jobs. Paid 512 MB service: $5.40/month; 1 GB: $12/month. | Good managed alternative, but the free allowance does not cover this stack and paid always-on services exceed the cap. |
| **Koyeb** | Free instance has 512 MB, cannot run as a worker or attach a persistent volume. Paid 1 GB Eco compute is $5.36/month. | Useful for a small API; not a free home for the required persistent infrastructure and workers. |
| **Azure Container Apps + student credits** | Managed container platform; eligible students can receive $100 credit valid for 12 months without a credit card. | Possible credit-funded alternative, but eligibility, regional quotas, stateful storage and total cost need a separate validation. Credits are temporary, not a recurring EUR 10 hosting plan. |

Sources: [Railway pricing](https://docs.railway.com/pricing), [Northflank pricing](https://northflank.com/pricing), [Koyeb instance limits/prices](https://www.koyeb.com/docs/reference/instances), [Azure Container Apps](https://learn.microsoft.com/en-us/azure/container-apps/overview), [Azure for Students](https://azure.microsoft.com/en-us/free/students/).

### Recommended Railway layout

Keep all source code in DocFlow. Deploy each application Dockerfile as a separate service in one project/region. Railway maps Compose concepts to individual services; it does **not** execute the Compose file directly. Keep Compose as the portable local/grading definition. [Compose migration guide](https://docs.railway.com/guides/docker-compose).

| Service | Exposure | Persistent data |
| --- | --- | --- |
| React/Nginx | Public HTTPS; `/api` proxies to the API | None; static files built into image |
| Spring Boot API | Private; both browser and installed PWA use the Nginx `/api` proxy | PostgreSQL and MinIO through private endpoints |
| PostgreSQL | Private | Dedicated volume |
| RabbitMQ | Private; management UI not public | Dedicated volume for durable queues |
| MinIO | Private; browser downloads stream through API | Dedicated volume |
| Elasticsearch | Private, single node | Dedicated volume |
| OCR, indexing and GenAI | Three private worker services | Temporary work files; durable results in MinIO/database |
| XML batch | Long-lived application with an in-process scheduler | Dedicated input/archive volume; see section 8 |
| Logstash | Private ingestion endpoint; separate container in every full session | Bounded memory buffer; diagnostic logs may be lost on shutdown |
| Kibana | Separate container; authenticated team-only access for demonstrations | Saved objects in Elasticsearch |

The same Nginx image serves the React build locally and online; a second frontend provider is unnecessary. Configure SPA fallback to `index.html`, an API proxy, upload/time-out limits and runtime upstream resolution. Browser code uses relative `/api` URLs and never private hostnames. The installed PWA uses the same public origin and `/api` path, so no separate mobile API endpoint is needed. Bind public web services to `0.0.0.0` and the platform's assigned port; keep credentials in backend environment secrets. A provider-generated domain avoids buying a domain.

Start with a small synthetic document collection and OCR concurrency one. Measure JVM/native memory, Elasticsearch startup and indexing, PDF rasterization, queue recovery and disk persistence in the first hosted spike. These have not yet been tested on Railway. A managed container host does not remove responsibility for database upgrades, backups or application recovery.

Five volumes cover PostgreSQL, RabbitMQ, MinIO, Elasticsearch and the batch folder. Railway currently documents ten volumes per Hobby project and a default 5 GB volume size. Verify account limits before provisioning. Treat container filesystems outside volumes as disposable. Keep reusable synthetic source documents and fixtures off-platform. PostgreSQL volumes do not prevent Hibernate from recreating mapped tables at application startup; restoring rows before that startup would not preserve them. Rebuild demonstration data after schema creation. [Volume limits](https://docs.railway.com/volumes/reference).

Plan for one billing/platform owner on Hobby, with all three students collaborating through GitHub and the controlled CI/CD workflows. Railway reserves workspace collaboration for Pro/Enterprise; do not assume three full workspace seats on Hobby or share a personal login. Verify project-level access separately. If all three need workspace administration, the $20 Pro minimum exceeds this budget. [Workspace access](https://docs.railway.com/projects/workspaces), [plan policy](https://docs.railway.com/pricing/plans).

### Session budget and shutdown

A planning example, **not a benchmark or guaranteed bill**: 30 running hours/month, average aggregate 6 GB RAM and one actively consumed vCPU, using a 730-hour month:

- Memory: `6 * $10 * 30 / 730 = $2.47`.
- CPU: `1 * $20 * 30 / 730 = $0.82`.
- 10 GB aggregate stored for the full month: `$1.50`.
- Total resources before egress: approximately `$4.79`; the $5 Hobby minimum applies. Tax, exchange fees, AI, backup storage and extra usage must still fit inside EUR 10.

Six GB is an illustrative average for the complete stack, not a promise that every component including ELK will fit or stay at that consumption. Measure actual startup/idle/processing usage, then reduce session hours if necessary. At the same average RAM, continuous operation alone would cost $60/month for memory, before other resources. Plan a two-hour trial first; do not buy larger plans merely to satisfy an unverified estimate.

1. Set a monthly resource allowance converted from the EUR 10 total after reserving tax, currency margin and any AI expense. Configure alerts and a compute hard limit; verify which invoice items are included. Do not assume a dollar usage cap equals a euro invoice cap. Disable unused platform AI/agent features.
2. Start only for a booked session, deploy the tested commit, let schema creation complete, bootstrap demonstration data and reconcile external state before allowing workers to process jobs. Wait for all dependencies and workers to become ready. Count startup and shutdown time as billable.
3. At session end stop all running deployments, retaining services, configuration and volumes. Verify the platform dashboard shows no running compute. Stopping a deployment is distinct from deleting a service or volume. Stored data continues to incur charges. [Deployment actions](https://docs.railway.com/deployments/reference).
4. Do not rely on automatic sleep: database connections, queue heartbeats and worker polling may prevent inactivity, and a queued message is not necessarily a wake-up trigger. Explicitly start/stop the complete dependency graph. [Cost controls](https://docs.railway.com/pricing/cost-control).
5. Use the primary local Compose environment for the demonstration if the spending limit or provider availability prevents a cloud session. A cloud URL will intentionally be unavailable between sessions.

Use separate, bounded application LLM quotas; platform compute caps do not cap Gemini charges. Prefer available free API quota for synthetic demos only after verifying account eligibility and data terms. CI uses deterministic stubs. No paid AI budget or guaranteed free quota is assumed.

## 8. Batch and mobile

The batch job imports **external access logs**, not OCR jobs. Supply an XSD and sample XML containing source-system ID, stable event ID, document reference, timestamp and action. Disable external XML entity resolution. Deduplicate by source/event ID and file checksum; commit audit rows/import status before archiving. Keep failed files and reasons separately; reruns must not duplicate data.

Configure schedule, timezone, input path, filename pattern and archive/error paths. Proposed daily schedule: 01:00 `Europe/Vienna`. Run the separate batch application as a long-lived process using Spring `@Scheduled`, preserving literal folder semantics. Each scheduled invocation calls the import service within the running application; it must not restart Spring or recreate the persistence context. With `ddl-auto=create`, a new application process would erase its previous audit/import records. Restarting the application still intentionally resets those tables; restarting it for every daily run is not the design. Hosted files need a delivery path: API uploads XML to a MinIO inbox prefix and the batch worker stages it into its own folder; it cannot mount the API's disk. Keep this staging adapter small; no general file-delivery framework is needed.

During scheduled cloud sessions, run the separate batch application with its own volume and demonstrate both the configured scheduler and a controlled manual trigger. It cannot run daily while the deployment is stopped. The local Compose deployment provides the daily scheduler when running. No continuous cloud execution is planned; this is a confirmed team decision. Object-storage delivery is only a staging mechanism; retain the required folder scanning, configured pattern and archive behavior.

Demonstrate the scheduler itself by configuring a near-future execution time while the environment is running, then verify the recorded trigger time, imported rows and archive outcome. Restore the documented daily schedule afterward. The user confirms that this demonstration of configurable scheduling is sufficient; no unattended daily execution between sessions is required. A manual trigger can supplement the demonstration but does not replace exercising the scheduler.

### Mobile deliverable: PWA

Use the same React application as an installable PWA. The user confirms that this satisfies the Sprint 5 smartphone requirement. No Capacitor, Android project, React Native or separate mobile codebase is planned.

Generate the Web App Manifest and service worker with `vite-plugin-pwa` (Workbox), configured for name, start URL, scope, standalone display mode and suitable icons; serve over HTTPS. Use responsive upload/list/detail/search/classification-review screens and show processing status on a phone. Build the PWA using AI coding assistance and record the generated work and human verification for the course requirement.

Cache only the public app shell and versioned static assets. Do not cache login responses, API requests, document downloads or user data. Upload, search and processing require connectivity; show an explicit offline state instead of pretending these work offline. Keep tokens in memory as specified in section 9. Provide a controlled update/reload flow to avoid mixing old frontend assets with a new release.

Include a real-phone acceptance check: install or add to home screen, launch standalone, log in, upload a sample PDF, search, inspect results and confirm multiple labels. Check offline messaging, logout and an app update. Browser install behavior varies; document the tested browser/device and provide instructions. `vite-plugin-pwa` replaces the earlier hand-written manifest/`sw.js` approach; it introduces no runtime framework beyond Workbox, which it bundles.

## 9. Authentication and operations

### Confirmed account behavior

Admins create accounts containing a username, email and role. Existing users log in with **either username or email plus password**. There is no sign-up screen, public registration endpoint, social login or separate paid identity provider.

Use Spring Security 7.1.1 and PostgreSQL-backed users. Confirmed role model: USER handles their own documents and classification reviews; ADMIN additionally manages accounts and taxonomy. Administrator status does not automatically confer access to everyone else's documents. Enforce object ownership on content, search, metadata, category-confirmation and retry endpoints.

Normalize login identifiers consistently (trim/lowercase for lookup), enforce database uniqueness on normalized username and email, and disallow `@` in usernames so username/email lookup cannot be ambiguous. Never trim or lowercase passwords. Use Spring's DelegatingPasswordEncoder with bcrypt, tune its work factor on target hardware and reject passwords beyond bcrypt's 72-byte input limit rather than truncating them. Never store or log raw passwords. [Spring password storage](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html).

Admins create a temporary password which is delivered directly to the intended user and must be changed at first login. A must-change-password account receives only password-change/logout access until completion. Admin reset follows the same flow; no email service or public forgot-password workflow is needed initially. Bootstrap the first admin via an explicit one-time setup command and environment secrets, never a committed default password or a startup process that repeatedly resets it. Prevent disabling/demoting the last active admin.

### Recommended authentication transport

Use a short-lived signed JWT access token (accepted default lifetime 15 minutes), issued after Spring Security authenticates the password. Use Spring Security's JWT encoder/decoder and JOSE/resource-server support at the existing managed version; no custom cryptography or extra authentication service. Validate signature, issuer, audience and expiry. This is a local login/token protocol, not a claim of implementing a complete OAuth authorization server. [Spring JWT support](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html).

For the student MVP keep the token **in memory**, send it explicitly in the Authorization header, and require login again after expiry or reload. Do not persist passwords or tokens in localStorage. This avoids adding refresh-token and cross-site-cookie complexity. The installed PWA uses the same in-memory approach; persistent login is a later feature. Read account enabled/password-change state and token version on authenticated requests; increment token version on password change, reset, disablement and logout to revoke outstanding tokens (logout is global for this simple design).

Use HTTPS and exact allowed origins; do not enable credential cookies. Limit login attempts and return the same outward message for unknown login/wrong password. Disable browser HTTP Basic/form-login defaults for the bearer API; only a strictly bearer-only API can omit cookie-oriented CSRF protection. If cookie authentication is added later, revisit CSRF/SameSite/credentials together.

Keep provider/infrastructure credentials on backend services. Use synthetic demo documents. Bound PDF size/pages, queue concurrency, request rates, prompt input/output and AI calls. Log IDs, stages, durations and error categories, not document content or credentials. Carry correlation IDs across HTTP/queues. Provide health reporting, failed-job visibility and authenticated retry/reindex actions.

## 10. CI/CD and tests

Use a lightweight GitFlow arrangement for the rubric: feature branches -> PRs to `develop`, release PRs -> `main`, sprint tags. Protect branches with required checks/reviews, keep GitHub Projects current and link issues to PRs.

### Submission contract (provided by the team)

- Join a Moodle GROUP before submission; all three students belong to that group.
- Submit one ZIP containing every required project source using the IDE export function, as instructed.
- After the deadline, the lecturer builds code from **MAIN at an unspecified time**. The ZIP/tag does not freeze what they will grade: whatever is then on main may be used.
- Keep unfinished work on feature/develop branches. Merge to main only complete, tested sprint increments. Protect main against direct/force pushes, require one other student's review, and require the always-present build/test gate on the actual merge candidate.
- Before submission, build a clean main checkout with documented Docker commands, export that same checkout, verify the ZIP includes both Java and React plus infrastructure/fixtures, and record its SHA in submission notes/tag. IDE export alone is not proof of completeness.
- During the grading window keep main stable; continue next-sprint work on develop. Any later main fix/release must remain complete and buildable and preserve earlier sprint behavior. Do not rely on main being graded immediately after the deadline.
- Grading must not require paid cloud access: provide deterministic AI stubs/fixtures for tests, explicit live-AI configuration for demonstrations, and reproducible local infrastructure.


GitHub Actions on PRs and pushes to `develop`/`main` should:

1. Compile/test Java; lint, typecheck, test and build React.
2. Keep the 80% application line-coverage target as a small margin above the required >70%. Use meaningful tests and narrow generated-code exclusions.
3. Test business rules, input validation, authorization and custom mappings. Include the course-requested valid/invalid controller and business-component cases; mock external I/O and use PostgreSQL for repository integration checks.
4. Add a few integration scenarios as each sprint introduces infrastructure. The main scenario uploads a known PDF, finds it through search, reads a summary and confirms category suggestions. Stub Gemini for deterministic CI. Include a basic duplicate-message case, one AI failure case and repeated XML import; defer exhaustive crash-timing, stale-message and provider-error matrices.
5. Build images and provide a full Compose smoke-test command. Before each sprint submission, verify the complete relevant stack, including log ingestion through Logstash and visibility in Kibana. Expensive full-stack checks may run in a manual pre-release workflow; ordinary PR checks remain fast.
6. Publish reports and verify that the submitted ZIP contains the reviewed source/configuration/fixtures and excludes secrets/caches.

### Simple cloud deployment

Use a manually triggered GitHub Actions workflow to deploy a selected passing `main` commit, and a separate manual shutdown workflow after the demonstration. Keep automatic CI on PRs/pushes. Disable independent platform Git-triggered deployments to avoid unexpected paid sessions. Use scoped environment secrets, record the deployed commit and verify health plus one synthetic end-to-end scenario. Build/deploy all applications together; no affected-service detection, session feature flag or automatic rollback machinery is needed.

The session owner sets a shutdown reminder, runs the stop workflow and checks that paid compute has stopped. Keep the provider spending limits and LLM quotas described above. This is a deliberate manual operational responsibility; no automatic time-based shutdown guarantee is claimed.

Because `ddl-auto=create` resets tables, stop the old applications/workers before starting a fresh demo session. Let the API and long-lived batch application initialize their own tables once, reset stale demo messages/indexes/files as needed, bootstrap sample data, then start processing. Keep one schema-owning instance per set of tables. If deployment fails, manually redeploy a known-good commit and prepare fresh demonstration data; data-preserving rollback is outside scope.

Perform one small live AI check before demonstrations, since stubbed tests cannot verify provider integration. Document clean-checkout startup/test commands so grading can run locally without paid hosting.

## 11. Three-student delivery plan

| Primary owner | Initial focus | Cross-review |
| --- | --- | --- |
| Student A | API, persistence/mapping, accounts and permissions | Reviews worker result contracts and persistence |
| Student B | React, classification-review UX, admin UI and mobile | Reviews API behavior and acceptance tests |
| Student C | OCR/GenAI/indexing, Compose, CI/CD and batch | Reviews integration/security failure paths |

These are starting responsibilities, not permanent silos. Student C's workload grows in later sprints: A takes batch persistence/schema mappings and B contributes Playwright/integration scenarios and AI review behavior. Rotate the release/checklist role each sprint. Every PR has a different reviewer; all three rehearse explaining all components before reviews.

### Sprint plan

| Sprint | Deliverable |
| --- | --- |
| 1 | Java API, PostgreSQL, MinIO object storage for uploads, Hibernate schema creation, layered document/classification/category entities, MapStruct, repositories, REST/OpenAPI, tests, Compose and CI; classification domain/fixtures only, no live LLM |
| 2 | React/Nginx, dashboard/detail/upload, validation, authentication, taxonomy/assignment UI and initial managed-container deployment spike |
| 3 | RabbitMQ, direct publishing with visible failures/manual retry, empty OCR consumer, contracts, logging/errors and mid-term review |
| 4 | Real OCR on the MinIO-stored PDFs (storage integrated since Sprint 1), separate indexing worker, Elasticsearch, Logstash and Kibana containers locally and in cloud sessions; demonstrate HelloWorld.pdf search and log visibility |
| 5 | GenAI summary/classification, validated results, retries, review UI and installable PWA tested on a phone |
| 6 | Focused end-to-end tests, long-lived scheduled XML importer, manual retry checks, deployment smoke test, final docs/ZIP/review preparation |

## 12. Moodle material references

Course materials worth consulting during implementation. Store any downloads outside the DocFlow repository.

### Architecture and implementation references

| Material | When useful |
| --- | --- |
| [Layered design](https://moodle.technikum-wien.at/mod/resource/view.php?id=2435288), [Java/JPA](https://moodle.technikum-wien.at/mod/resource/view.php?id=2435292), [MapStruct](https://moodle.technikum-wien.at/mod/resource/view.php?id=2435305), [business layer](https://moodle.technikum-wien.at/mod/resource/view.php?id=2435315) | Align the design with teaching examples |
| [REST/OpenAPI](https://moodle.technikum-wien.at/mod/resource/view.php?id=2435263), [UI integration](https://moodle.technikum-wien.at/mod/resource/view.php?id=2435339), [OAuth2/JWT](https://moodle.technikum-wien.at/mod/resource/view.php?id=2435341) | API/authentication planning |
| [Distributed transactions](https://moodle.technikum-wien.at/mod/resource/view.php?id=2435363), [event-driven architecture](https://moodle.technikum-wien.at/mod/resource/view.php?id=2435370), [message brokers](https://moodle.technikum-wien.at/mod/resource/view.php?id=2435374) | Before Sprint 3 |
| [MinIO](https://moodle.technikum-wien.at/mod/resource/view.php?id=2435385), [OCR](https://moodle.technikum-wien.at/mod/resource/view.php?id=2435395), [Elasticsearch](https://moodle.technikum-wien.at/mod/resource/view.php?id=2435409), [Elasticsearch/Kibana lesson](https://moodle.technikum-wien.at/mod/lesson/view.php?id=2435412) | Implementation examples for storage, OCR, search and ELK |
| [Stability patterns](https://moodle.technikum-wien.at/mod/resource/view.php?id=2435422), [GenAI as a service](https://moodle.technikum-wien.at/mod/resource/view.php?id=2435452), [Spring AI](https://moodle.technikum-wien.at/mod/resource/view.php?id=2435456) | Confirm AI service/framework expectations |
| [AI-assisted software engineering](https://moodle.technikum-wien.at/mod/resource/view.php?id=2435460) | AI-assisted PWA generation and evidence requirements |
| [Integration tests](https://moodle.technikum-wien.at/mod/resource/view.php?id=2435467), [batch processing](https://moodle.technikum-wien.at/mod/resource/view.php?id=2435474) | Final testing and XML import requirements |
| Java examples: [JPA](https://moodle.technikum-wien.at/mod/url/view.php?id=2435296), [mapping](https://moodle.technikum-wien.at/mod/url/view.php?id=2435311), [GitHub Actions](https://moodle.technikum-wien.at/mod/url/view.php?id=2435428) | Optional; public destination URLs are sufficient |

## 13. Accepted decisions and remaining validation

No further architecture choice is required from the team now. The defaults have been accepted, with PWA replacing the earlier Capacitor proposal.

| Topic | Decision |
| --- | --- |
| Backend/frontend | Java 25 LTS / Spring Boot, React in the same repository |
| Ownership/accounts | Owner-only document access; admins maintain users and taxonomy; username/email/password, no registration |
| Classification | Seven initial labels, multi-label and mandatory human confirmation; live LLM work in Sprint 5 |
| AI provider | Gemini default; verify account/model availability, data terms and usage quota before live integration |
| Hosting | Compose primary; Railway demo sessions with manual deploy/stop workflows, no VPS or continuous online execution, EUR 10 team maximum |
| Mobile | Installable React PWA; confirmed sufficient by the user |
| Storage | Pinned MinIO source build |
| Database schema | Hibernate `ddl-auto=create`; recreate mapped tables at startup, no Flyway; disposable demonstration data |
| MVP reliability | Direct publishing, current processing status and manual retries; no outbox or distributed deletion workflow |
| Classification storage | Current suggestions and confirmed assignments; historical runs and feedback records deferred |
| ELK | Elasticsearch, Logstash and Kibana as containers both locally and in cloud sessions |
| Batch | Long-lived application with Spring scheduling; no restart per import; demonstrating a configurable daily scheduler is sufficient |

### Confirmed course interpretations

The user confirmed the following on 14 October 2026. These questions are resolved; implementation and demonstration checks remain.

| Topic | Confirmed requirement |
| --- | --- |
| Team size | Three students. |
| Smartphone deliverable | The installable React PWA satisfies the requirement. Demonstrate its functionality on a real phone and retain the AI-assistance record. |
| Daily importer | Demonstrating a configurable daily scheduler is sufficient. Exercise a scheduled trigger and verify import/archive results; continuous execution between sessions is not required. |

Remaining implementation checks: measure the complete hosted stack including ELK, verify account limits and shutdown controls, pin container digests/lockfiles, test version compatibility, validate the PWA on a real phone, and exercise the scheduled importer. These checks verify the implementation; no further course-acceptance clarification is pending for team size, PWA delivery or daily scheduling.
