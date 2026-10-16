# DocFlow — Sprint 1 Summary

**Course:** SWEN3 / BIF5

**Updated:** 16 October 2026

**Status:** The backend foundation and the required document metadata API are implemented. Document types and their many-to-many assignment to documents are available as an additional first domain feature.

## Sprint goal

Sprint 1 established DocFlow as a Spring Boot REST application. The current application manages document metadata in PostgreSQL and exposes the required health and document CRUD endpoints.

The implementation is a backend foundation, not yet a complete document management system. It stores metadata only; no PDF bytes are uploaded or stored.

## Implemented functionality

### Required document API

- Create a document metadata record
- List all document metadata records
- Retrieve one document by ID
- Replace the editable metadata of an existing document
- Delete an existing document
- Return **404 Not Found** when a requested document does not exist
- Generate the database ID and creation timestamp automatically
- Validate the document request before it reaches the service

A document currently contains:

| Field | Description |
| --- | --- |
| id | Database-generated identifier |
| filename | Required, non-blank, maximum 255 characters |
| contentType | Required, non-blank, maximum 255 characters |
| description | Optional, maximum 255 characters |
| createdAt | Automatically generated creation timestamp |

The content type is currently validated only as required text. The application does not inspect actual PDF content because the request contains JSON metadata rather than file bytes.

### Document types

The following additional Sprint 1 foundation is implemented:

- Create a document type
- List all document types
- Enforce a unique document type name at database level
- Assign an existing type to an existing document
- Persist the many-to-many relationship in the **document_document_types** table

A document can have multiple types, and a type can belong to multiple documents. Example types include Invoice, Contract, and Certificate.

The assignment is persisted but is not yet included in the document response. It therefore cannot currently be read through the document GET endpoint.

### Technical foundation

- Java 25 and Spring Boot 4.1.1
- Spring Web MVC for the REST API
- Spring Data JPA and Hibernate for persistence
- PostgreSQL 18.6 in Docker Compose
- H2 for the isolated Spring application-context test
- Separate request and response DTOs
- Manual mapping between DTOs and JPA entities
- Constructor injection in controllers and services
- Jakarta Bean Validation for document requests
- Maven Wrapper for reproducible builds
- Multi-stage Docker image for the backend
- GitHub Actions workflow for tests and Docker image verification

## Current architecture

~~~text
HTTP request
    -> Controller
    -> Service
    -> Spring Data repository
    -> PostgreSQL
~~~

The backend is organized as follows:

~~~text
backend/src/main/java/com/docflow/backend/
├── controller/   REST endpoints
├── dto/          Request and response models
├── entity/       JPA entities and relationships
├── mapper/       DTO/entity conversion
├── repository/   Spring Data repositories
└── service/      Application logic
~~~

## Required API contract

Default base URL: **http://localhost:8080**

| **Method** | **Route** | **Success** |
| --- | --- | --- |
| GET | /api/health | 200 |
| POST | /api/documents | 201 |
| GET | /api/documents | 200 |
| GET | /api/documents/{id} | 200 or 404 |
| PUT | /api/documents/{id} | 200 or 404 |
| DELETE | /api/documents/{id} | 204 or 404 |

The controllers implement this contract directly:

- POST returns **201 Created**.
- Successful reads and updates return **200 OK**.
- A successful delete returns **204 No Content**.
- Read, update, and delete return **404 Not Found** for an unknown document ID.
- Invalid document requests return **400 Bad Request**.

### Additional document-type endpoints

| Method | Route | Success |
| --- | --- | --- |
| POST | /api/document-types | 201 |
| GET | /api/document-types | 200 |
| POST | /api/documents/{documentId}/types/{typeId} | 200 |

These endpoints extend the required core contract without changing its routes or status codes.

## Persistence and configuration

The application uses PostgreSQL by default. The main variables are documented in **.env.example**:

| Variable | Example/default | Purpose |
| --- | --- | --- |
| POSTGRES_DB | docflow | Database name |
| POSTGRES_USER | docflow | Database user |
| POSTGRES_PASSWORD | docflow | Local example password; change it for your environment |
| POSTGRES_PORT | 5432 | PostgreSQL host port |
| API_PORT | 8080 | Backend host port with Docker Compose |
| SERVER_PORT | 8080 | Backend port when Java is started directly |

Hibernate currently uses **spring.jpa.hibernate.ddl-auto=update**. It updates the schema at startup, while PostgreSQL data is retained in the named **postgres18_data** volume. Changing credentials in **.env** does not change the credentials of an already initialized database volume.

## Tests and continuous integration

Run the complete backend verification from the backend directory:

~~~bash
./mvnw -B -ntp clean verify
~~~

Verified result on 16 October 2026: **27 tests, 0 failures, 0 errors, 0 skipped**.

The test suite covers:

- Document service CRUD behavior
- Missing-document behavior for reads, updates, and deletes
- Document-type assignment logic
- Document controller routes, status codes, and request validation
- Health endpoint route, status, and response body
- Document type service and controller
- Complete Spring application-context startup with H2

GitHub Actions runs **clean verify** for pull requests and pushes to **dev** or **main**. After successful tests, it also verifies the backend Docker image build. A coverage gate and PostgreSQL integration tests are not configured yet.

## Known limitations

- Only metadata is stored; PDF upload and file storage are not implemented.
- MinIO or another object store is not connected.
- Authentication, authorization, and document ownership are not implemented.
- Document types currently provide only create and list operations.
- Assigned types are not included in document responses.
- Assigning a missing document or type does not yet produce a controlled 404 response.
- Document type requests do not yet have Bean Validation constraints.
- Lists are not paginated and have no guaranteed order.
- There is no centralized REST error representation.
- **contracts/openapi.yaml** represents an earlier API design and is not aligned with the current unversioned routes.
- Frontend, search, OCR, messaging, and AI classification are not implemented.

## Usage and demonstration

### Prerequisites

- Docker with Docker Compose v2
- JDK 25 when running the backend outside Docker
- Network access during the first build to download Maven dependencies and Docker images

### 1. Start the complete stack

Run the commands from the repository root:

~~~bash
cp .env.example .env
~~~

Change the password in **.env** for your local environment, then start the stack:

~~~bash
docker compose config --quiet
docker compose up --build --detach --wait
docker compose ps
~~~

Follow the backend logs when needed:

~~~bash
docker compose logs --follow backend
~~~

### 2. Verify the health endpoint

~~~bash
BASE_URL=http://localhost:8080
curl -i "$BASE_URL/api/health"
~~~

Expected status: **200 OK**

Expected body:

~~~text
DocFlow REST Server is running!
~~~

### 3. Create a document

~~~bash
curl -i -X POST "$BASE_URL/api/documents" \
  -H 'Content-Type: application/json' \
  -d '{"filename":"invoice.pdf","contentType":"application/pdf","description":"October electricity invoice"}'
~~~

Expected status: **201 Created**

The JSON response contains the generated ID. Use that value in the following commands:

~~~bash
DOCUMENT_ID=1
~~~

Replace 1 if the returned ID is different.

### 4. List and retrieve documents

~~~bash
curl -i "$BASE_URL/api/documents"
curl -i "$BASE_URL/api/documents/$DOCUMENT_ID"
~~~

Both successful requests return **200 OK**.

### 5. Update document metadata

~~~bash
curl -i -X PUT "$BASE_URL/api/documents/$DOCUMENT_ID" \
  -H 'Content-Type: application/json' \
  -d '{"filename":"invoice-october.pdf","contentType":"application/pdf","description":"Updated electricity invoice"}'
~~~

Expected status: **200 OK**

### 6. Demonstrate request validation

~~~bash
curl -i -X POST "$BASE_URL/api/documents" \
  -H 'Content-Type: application/json' \
  -d '{"filename":"","contentType":"application/pdf","description":"Invalid example"}'
~~~

Expected status: **400 Bad Request**

### 7. Create and assign a document type

Create a type:

~~~bash
curl -i -X POST "$BASE_URL/api/document-types" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Invoice","description":"Invoices and payment requests"}'
~~~

Expected status: **201 Created**

List the types and copy the returned ID:

~~~bash
curl -i "$BASE_URL/api/document-types"
TYPE_ID=1
~~~

Replace 1 if the returned ID is different, then assign the type:

~~~bash
curl -i -X POST "$BASE_URL/api/documents/$DOCUMENT_ID/types/$TYPE_ID"
~~~

Expected status: **200 OK** with an empty body.

Because assigned types are not yet exposed in the document response, the persisted join row can be shown directly in PostgreSQL:

~~~bash
docker compose exec db sh -c \
  'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT * FROM document_document_types;"'
~~~

### 8. Delete the document and demonstrate 404

~~~bash
curl -i -X DELETE "$BASE_URL/api/documents/$DOCUMENT_ID"
~~~

Expected status: **204 No Content**

Requesting the deleted document demonstrates the required missing-resource behavior:

~~~bash
curl -i "$BASE_URL/api/documents/$DOCUMENT_ID"
~~~

Expected status: **404 Not Found**

### 9. Stop the stack

~~~bash
docker compose down
~~~

This retains the PostgreSQL volume. To deliberately delete all local DocFlow database data:

~~~bash
docker compose down --volumes
~~~

### Alternative: run the backend with Java

Start only PostgreSQL:

~~~bash
docker compose up --detach --wait db
~~~

Load the trusted local **.env** file into the shell and start Spring Boot:

~~~bash
set -a
source .env
set +a
cd backend
./mvnw spring-boot:run
~~~

Alternatively, build and run the executable JAR:

~~~bash
cd backend
./mvnw -B -ntp clean package
java -jar target/backend-0.0.1-SNAPSHOT.jar
~~~

Reusable HTTP requests are also available in **tests/http/documents.http**.
