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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;

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
    private TestHttpClient http;

    @BeforeEach
    void configureClient() {
        http = new TestHttpClient(port);
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
        var accepted = http.upload(parts(pdf, "../invoice.pdf", "Invoice"));
        assertThat(accepted.getStatusCode().value()).isEqualTo(202);
        long id = http.json(accepted).get("id").asLong();
        String path = "/api/v1/documents/" + id;
        assertThat(accepted.getHeaders().getLocation().toString()).isEqualTo(path + "/processing");
        assertThat(http.json(accepted).get("statusUrl").asString()).isEqualTo(path + "/processing");
        var original = service.getDocumentById(demoOwner(), id).orElseThrow();
        assertThat(original.filename()).isEqualTo("invoice.pdf");
        assertThat(original.title()).isEqualTo("Invoice");
        assertThat(original.originalObjectKey()).matches("documents/[a-f0-9-]{36}\\.pdf");
        assertThat(http.json(http.request("GET", path + "/processing", null)).get("status").asString()).isEqualTo("DEFERRED");
        var download = http.request("GET", path + "/content", null);
        assertThat(download.getStatusCode().value()).isEqualTo(200);
        assertThat(download.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(download.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).startsWith("attachment;");
        assertThat(download.getBody()).isEqualTo(pdf);
        assertThat(http.request("PUT", path + "/metadata", "{\"filename\":\"renamed.pdf\",\"contentType\":\"text/plain\",\"title\":\"Renamed\"}")
                .getStatusCode().value()).isEqualTo(200);
        assertThat(http.request("GET", path + "/content", null).getBody()).isEqualTo(pdf);
        assertThat(service.getDocumentById(demoOwner(), id).orElseThrow().originalObjectKey()).isEqualTo(original.originalObjectKey());
        assertThat(http.request("DELETE", path, null).getStatusCode().value()).isEqualTo(204);
        assertThatThrownBy(() -> storage.get(original.originalObjectKey())).isInstanceOf(DocumentContentNotFoundException.class);
        http.assertProblem(http.request("GET", path + "/content", null), 404);
        assertThat(users.existsById(demoOwner())).isTrue();
    }

    @Test
    void rejectsInvalidAndOversizedPdfWithoutSavingAnything() throws Exception {
        http.assertProblem(http.upload(parts("not PDF".getBytes(StandardCharsets.UTF_8), "fake.pdf", "Invalid")), 400);
        http.assertProblem(http.upload(parts(new byte[4097], "large.pdf", "Too large")), 413);
        http.assertProblem(http.upload(parts(new byte[9000], "large.pdf", "Too large")), 413);
        assertThat(documents.count()).isZero();
        assertThat(objectCount()).isZero();
    }

    @Test
    void contentAndStatusAreOwnerScopedAndFormFieldsCannotChooseTheOwner() throws Exception {
        long other = users.saveAndFlush(new UserEntity("upload-other")).getId();
        byte[] pdf = PdfFixtures.pdf(1);
        var privateDocument = files.upload(other, new DocumentMetadata("private.pdf", "application/pdf", null), pdf);
        String path = "/api/v1/documents/" + privateDocument.id();
        http.assertProblem(http.request("GET", path + "/content", null), 404);
        http.assertProblem(http.request("GET", path + "/processing", null), 404);
        http.assertProblem(http.request("DELETE", path, null), 404);
        assertThat(storage.get(privateDocument.originalObjectKey())).isEqualTo(pdf);
        var form = parts(pdf, "demo.pdf", "Demo");
        form.add("ownerId", Long.toString(other));
        form.add("originalObjectKey", "documents/forged.pdf");
        var accepted = http.upload(form);
        assertThat(accepted.getStatusCode().value()).isEqualTo(202);
        var saved = service.getDocumentById(demoOwner(), http.json(accepted).get("id").asLong()).orElseThrow();
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
        http.assertProblem(http.request("GET", "/api/v1/documents/" + missing.id() + "/content", null), 404);
    }

    @Test
    void openApiDescribesMultipartCreationAndSuccessfulProcessingResponse() {
        var paths = http.json(http.request("GET", "/v3/api-docs", null)).get("paths");
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
}
