package at.fhtw.docflow;

import at.fhtw.docflow.document.application.DocumentContentNotFoundException;
import at.fhtw.docflow.document.application.DocumentFileUseCases;
import at.fhtw.docflow.document.application.DocumentStorage;
import at.fhtw.docflow.document.application.DocumentUseCases;
import at.fhtw.docflow.document.domain.DocumentMetadata;
import at.fhtw.docflow.document.persistence.DocumentRepository;
import at.fhtw.docflow.identity.persistence.UserEntity;
import at.fhtw.docflow.identity.persistence.UserRepository;
import io.minio.BucketExistsArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.*;

@ActiveProfiles("demo")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DocumentUploadIT extends PostgresIntegrationTest {
    @Container
    static final GenericContainer<?> minio = new GenericContainer<>(new ImageFromDockerfile()
            .withFileFromPath("Dockerfile", Path.of("../../../infra/minio/Dockerfile").toAbsolutePath().normalize()))
            .withEnv("MINIO_ROOT_USER", "test-owner").withEnv("MINIO_ROOT_PASSWORD", "test-storage-only")
            .withEnv("MINIO_BROWSER", "off").withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000)).withStartupTimeout(Duration.ofSeconds(90));

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("docflow.storage.endpoint", () -> "http://" + minio.getHost() + ":" + minio.getMappedPort(9000));
        registry.add("docflow.upload.max-bytes", () -> 4096);
        registry.add("docflow.upload.max-pages", () -> 2);
        registry.add("spring.servlet.multipart.max-request-size", () -> "8192B");
    }

    @LocalServerPort
    int port;
    @Autowired
    DocumentStorage storage;
    @Autowired
    DocumentUseCases service;
    @Autowired
    DocumentFileUseCases files;
    @Autowired
    UserRepository users;
    @Autowired
    DocumentRepository documents;
    @Autowired
    MinioClient minioClient;
    private RestClient http;
    private final JsonMapper mapper = JsonMapper.builder().build();

    @BeforeEach
    void configureClient() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));
        http = RestClient.builder().baseUrl("http://localhost:" + port).requestFactory(factory).build();
    }

    @AfterEach
    void clearDocuments() {
        for (var document : documents.findAll()) {
            if (document.getOriginalObjectKey() != null) {
                storage.delete(document.getOriginalObjectKey());
            }
        }
        documents.deleteAll();
    }

    @Test
    void uploadsDownloadsEditsAndDeletesOriginalPdf() throws Exception {
        byte[] pdf = PdfFixtures.pdf(1);
        var accepted = upload(parts(pdf, "../invoice.pdf", "Invoice"));
        assertThat(accepted.getStatusCode().value()).isEqualTo(202);
        long id = json(accepted).get("id").asLong();
        String path = "/api/v1/documents/" + id;
        assertThat(accepted.getHeaders().getLocation().toString()).isEqualTo(path + "/processing");
        assertThat(json(accepted).get("statusUrl").asString()).isEqualTo(path + "/processing");
        var original = service.getDocumentById(demoOwner(), id).orElseThrow();
        assertThat(original.filename()).isEqualTo("invoice.pdf");
        assertThat(original.title()).isEqualTo("Invoice");
        assertThat(original.originalObjectKey()).matches("documents/[a-f0-9-]{36}\\.pdf");
        assertThat(json(request("GET", path + "/processing", null)).get("status").asString()).isEqualTo("DEFERRED");
        var download = request("GET", path + "/content", null);
        assertThat(download.getStatusCode().value()).isEqualTo(200);
        assertThat(download.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(download.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).startsWith("attachment;");
        assertThat(download.getBody()).isEqualTo(pdf);
        assertThat(request("PUT", path + "/metadata", "{\"filename\":\"renamed.pdf\",\"contentType\":\"text/plain\",\"title\":\"Renamed\"}")
                .getStatusCode().value()).isEqualTo(200);
        assertThat(request("GET", path + "/content", null).getBody()).isEqualTo(pdf);
        assertThat(service.getDocumentById(demoOwner(), id).orElseThrow().originalObjectKey()).isEqualTo(original.originalObjectKey());
        assertThat(request("DELETE", path, null).getStatusCode().value()).isEqualTo(204);
        assertThatThrownBy(() -> storage.get(original.originalObjectKey())).isInstanceOf(DocumentContentNotFoundException.class);
        assertProblem(request("GET", path + "/content", null), 404);
        assertThat(users.existsById(demoOwner())).isTrue();
    }

    @Test
    void rejectsInvalidAndOversizedPdfWithoutSavingAnything() throws Exception {
        assertProblem(upload(parts("not PDF".getBytes(StandardCharsets.UTF_8), "fake.pdf", "Invalid")), 400);
        assertProblem(upload(parts(new byte[4097], "large.pdf", "Too large")), 413);
        assertProblem(upload(parts(new byte[9000], "large.pdf", "Too large")), 413);
        assertThat(documents.count()).isZero();
        assertThat(objectCount()).isZero();
    }

    @Test
    void contentAndStatusAreOwnerScopedAndFormFieldsCannotChooseTheOwner() throws Exception {
        long other = users.saveAndFlush(new UserEntity("upload-other")).getId();
        byte[] pdf = PdfFixtures.pdf(1);
        var privateDocument = files.upload(other, new DocumentMetadata("private.pdf", "application/pdf", null), pdf);
        String path = "/api/v1/documents/" + privateDocument.id();
        assertProblem(request("GET", path + "/content", null), 404);
        assertProblem(request("GET", path + "/processing", null), 404);
        assertProblem(request("DELETE", path, null), 404);
        assertThat(storage.get(privateDocument.originalObjectKey())).isEqualTo(pdf);
        var form = parts(pdf, "demo.pdf", "Demo");
        form.add("ownerId", Long.toString(other));
        form.add("originalObjectKey", "documents/forged.pdf");
        var accepted = upload(form);
        assertThat(accepted.getStatusCode().value()).isEqualTo(202);
        var saved = service.getDocumentById(demoOwner(), json(accepted).get("id").asLong()).orElseThrow();
        assertThat(saved.ownerId()).isEqualTo(demoOwner());
        assertThat(saved.originalObjectKey()).isNotEqualTo("documents/forged.pdf");
    }

    @Test
    void databaseFailureRemovesTheActualUploadedObject() throws Exception {
        long before = objectCount();
        assertThatThrownBy(() -> files.upload(Long.MAX_VALUE,
                new DocumentMetadata("rollback.pdf", "application/pdf", null), PdfFixtures.pdf(1)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(objectCount()).isEqualTo(before);
        assertThat(documents.count()).isZero();
    }

    @Test
    void missingOriginalHasAProblemResponse() {
        var missing = service.createStoredDocument(demoOwner(), new DocumentMetadata("missing.pdf", "application/pdf", null), "documents/missing.pdf");
        assertProblem(request("GET", "/api/v1/documents/" + missing.id() + "/content", null), 404);
    }

    @Test
    void openApiDescribesMultipartCreationAndSuccessfulProcessingResponse() {
        var paths = json(request("GET", "/v3/api-docs", null)).get("paths");
        var post = paths.get("/api/v1/documents").get("post");
        var content = post.get("requestBody").get("content");
        assertThat(content.has("application/json")).isFalse();
        assertThat(content.has("multipart/form-data")).isTrue();
        assertThat(post.get("responses").has("201")).isFalse();
        assertThat(post.get("responses").has("202")).isTrue();
        var processing = paths.get("/api/v1/documents/{id}/processing").get("get").get("responses");
        assertThat(processing.get("200").get("content").get("application/json").get("schema").has("$ref")).isTrue();
        assertThat(processing.has("413")).isFalse();
        assertThat(paths.get("/api/v1/documents/{id}/content").get("get").get("responses").get("200")
                .get("content").has("application/pdf")).isTrue();
    }

    private long demoOwner() {
        return users.findByUsername("demo").orElseThrow().getId();
    }

    private long objectCount() throws Exception {
        long count = 0;
        if (minioClient.bucketExists(BucketExistsArgs.builder().bucket("docflow").build())) {
            for (var item : minioClient.listObjects(ListObjectsArgs.builder().bucket("docflow").recursive(true).build())) {
                item.get();
                count++;
            }
        }
        return count;
    }

    private MultiValueMap<String, Object> parts(byte[] pdf, String filename, String title) {
        var form = new LinkedMultiValueMap<String, Object>();
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        var resource = new ByteArrayResource(pdf) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        form.add("file", new HttpEntity<>(resource, headers));
        form.add("title", title);
        return form;
    }

    private ResponseEntity<byte[]> upload(MultiValueMap<String, Object> form) {
        return http.post().uri("/api/v1/documents").contentType(MediaType.MULTIPART_FORM_DATA).body(form)
                .exchange((request, response) -> new ResponseEntity<>(response.bodyTo(byte[].class), response.getHeaders(), response.getStatusCode()));
    }

    private ResponseEntity<byte[]> request(String method, String path, String body) {
        var request = http.method(HttpMethod.valueOf(method)).uri(path);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        return request.exchange((sent, response) -> new ResponseEntity<>(response.bodyTo(byte[].class), response.getHeaders(), response.getStatusCode()));
    }

    private JsonNode json(ResponseEntity<byte[]> response) {
        return mapper.readTree(response.getBody());
    }

    private void assertProblem(ResponseEntity<byte[]> response, int status) {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(json(response).get("status").asInt()).isEqualTo(status);
    }
}
