package at.fhtw.docflow;

import at.fhtw.docflow.document.application.DocumentStorage;
import at.fhtw.docflow.document.application.DocumentUseCases;
import at.fhtw.docflow.document.domain.Document;
import at.fhtw.docflow.document.domain.DocumentMetadata;
import at.fhtw.docflow.document.persistence.DocumentRepository;
import at.fhtw.docflow.identity.persistence.UserEntity;
import at.fhtw.docflow.identity.persistence.UserRepository;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

@ActiveProfiles("demo")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DocumentApiIT extends PostgresIntegrationTest {
    @LocalServerPort
    int port;
    @MockitoBean
    DocumentStorage storage;
    @Autowired
    DocumentUseCases service;
    @Autowired
    UserRepository users;
    @Autowired
    DocumentRepository documents;
    private RestClient http;
    private final JsonMapper mapper = JsonMapper.builder().build();

    @BeforeEach
    void configureClient() {
        when(storage.available()).thenReturn(true);
        var factory = new SimpleClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(15));
        http = RestClient.builder().baseUrl("http://localhost:" + port).requestFactory(factory).build();
    }

    @AfterEach
    void clearDocuments() {
        documents.deleteAll();
    }

    @Test
    void readsEditsAndDeletesAServiceFixture() {
        Document original = fixture(users.findByUsername("demo").orElseThrow().getId(), "invoice");
        String path = "/api/v1/documents/" + original.id();
        assertThat(json(request("GET", path, null)).get("title").asString()).isEqualTo("invoice");
        assertThat(json(request("GET", "/api/v1/documents", null)).size()).isEqualTo(1);
        var updated = request("PUT", path + "/metadata", "{\"filename\":\"renamed.pdf\",\"contentType\":\"application/pdf\",\"title\":\"New title\",\"description\":null}");
        assertThat(updated.getStatusCode().value()).isEqualTo(200);
        assertThat(json(updated).get("id").asLong()).isEqualTo(original.id());
        assertThat(json(updated).get("title").asString()).isEqualTo("New title");
        assertThat(json(updated).has("ownerId")).isFalse();
        assertThat(json(updated).has("originalObjectKey")).isFalse();
        Document persisted = service.getDocumentById(original.ownerId(), original.id()).orElseThrow();
        assertThat(persisted.createdAt()).isEqualTo(original.createdAt());
        assertThat(persisted.originalObjectKey()).isEqualTo(original.originalObjectKey());
        assertThat(persisted.description()).isNull();
        assertThat(request("DELETE", path, null).getStatusCode().value()).isEqualTo(204);
        assertProblem(request("GET", path, null), 404);
        assertProblem(request("DELETE", path, null), 404);
    }

    @Test
    void rejectsInvalidMetadataProtectedFieldsAndJsonCreation() {
        assertProblem(request("POST", "/api/v1/documents", "{}"), 415);
        assertProblem(request("PUT", "/api/v1/documents/1/metadata", "{}"), 400);
        assertProblem(request("PUT", "/api/v1/documents/1/metadata", "{"), 400);
        assertProblem(request("GET", "/api/v1/documents/invalid", null), 400);
        for (String field : new String[]{"ownerId", "originalObjectKey", "createdAt", "id"}) {
            assertProblem(request("PUT", "/api/v1/documents/1/metadata",
                    "{\"filename\":\"test.pdf\",\"contentType\":\"application/pdf\",\"" + field + "\":\"forged\"}"), 400);
        }
        assertThat(documents.count()).isZero();
    }

    @Test
    void cannotListReadEditOrDeleteAnotherOwnersFixture() {
        long other = users.saveAndFlush(new UserEntity("other-owner")).getId();
        Document original = fixture(other, "private");
        String path = "/api/v1/documents/" + original.id();
        assertThat(json(request("GET", "/api/v1/documents", null)).size()).isZero();
        assertProblem(request("GET", path, null), 404);
        assertProblem(request("PUT", path + "/metadata", "{\"filename\":\"forged.pdf\",\"contentType\":\"application/pdf\"}"), 404);
        assertProblem(request("DELETE", path, null), 404);
        assertThat(service.getDocumentById(other, original.id())).contains(original);
    }

    @Test
    void healthIncludesStorageButLivenessDoesNot() {
        for (String path : new String[]{"/actuator/health", "/actuator/health/readiness", "/actuator/health/liveness"}) {
            var response = request("GET", path, null);
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(json(response).get("status").asString()).isEqualTo("UP");
            assertThat(json(response).has("components")).isFalse();
        }
        when(storage.available()).thenReturn(false);
        assertThat(request("GET", "/actuator/health/readiness", null).getStatusCode().value()).isEqualTo(503);
        assertThat(request("GET", "/actuator/health/liveness", null).getStatusCode().value()).isEqualTo(200);
    }

    private Document fixture(long ownerId, String name) {
        return service.createStoredDocument(ownerId, new DocumentMetadata(name + ".pdf", "application/pdf", "Example", name),
                "documents/" + name + ".pdf");
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
