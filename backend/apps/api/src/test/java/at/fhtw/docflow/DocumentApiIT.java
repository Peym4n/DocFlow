package at.fhtw.docflow;

import at.fhtw.docflow.document.application.DocumentStorage;
import at.fhtw.docflow.document.application.DocumentUseCases;
import at.fhtw.docflow.document.domain.Document;
import at.fhtw.docflow.document.domain.DocumentMetadata;
import at.fhtw.docflow.document.persistence.DocumentRepository;
import at.fhtw.docflow.identity.persistence.UserEntity;
import at.fhtw.docflow.identity.persistence.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

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
    private TestHttpClient http;

    @BeforeEach
    void configureClient() {
        when(storage.available()).thenReturn(true);
        http = new TestHttpClient(port);
    }

    @AfterEach
    void clearDocuments() {
        documents.deleteAll();
    }

    @Test
    void readsEditsAndDeletesAServiceFixture() {
        Document original = fixture(users.findByUsername("demo").orElseThrow().getId(), "invoice");
        String path = "/api/v1/documents/" + original.id();
        assertThat(http.json(http.request("GET", path, null)).get("title").asString()).isEqualTo("invoice");
        assertThat(http.json(http.request("GET", "/api/v1/documents", null)).size()).isEqualTo(1);
        var updated = http.request("PUT", path + "/metadata", "{\"filename\":\"renamed.pdf\",\"contentType\":\"application/pdf\",\"title\":\"New title\",\"description\":null}");
        assertThat(updated.getStatusCode().value()).isEqualTo(200);
        assertThat(http.json(updated).get("id").asLong()).isEqualTo(original.id());
        assertThat(http.json(updated).get("title").asString()).isEqualTo("New title");
        assertThat(http.json(updated).has("ownerId")).isFalse();
        assertThat(http.json(updated).has("originalObjectKey")).isFalse();
        Document persisted = service.getDocumentById(original.ownerId(), original.id()).orElseThrow();
        assertThat(persisted.createdAt()).isEqualTo(original.createdAt());
        assertThat(persisted.originalObjectKey()).isEqualTo(original.originalObjectKey());
        assertThat(persisted.description()).isNull();
        assertThat(http.request("DELETE", path, null).getStatusCode().value()).isEqualTo(204);
        http.assertProblem(http.request("GET", path, null), 404);
        http.assertProblem(http.request("DELETE", path, null), 404);
    }

    @Test
    void rejectsInvalidMetadataProtectedFieldsAndJsonCreation() {
        http.assertProblem(http.request("POST", "/api/v1/documents", "{}"), 415);
        http.assertProblem(http.request("PUT", "/api/v1/documents/1/metadata", "{}"), 400);
        http.assertProblem(http.request("PUT", "/api/v1/documents/1/metadata", "{"), 400);
        http.assertProblem(http.request("GET", "/api/v1/documents/invalid", null), 400);
        for (String field : new String[]{"ownerId", "originalObjectKey", "createdAt", "id"}) {
            http.assertProblem(http.request("PUT", "/api/v1/documents/1/metadata",
                    "{\"filename\":\"test.pdf\",\"contentType\":\"application/pdf\",\"" + field + "\":\"forged\"}"), 400);
        }
        assertThat(documents.count()).isZero();
    }

    @Test
    void cannotListReadEditOrDeleteAnotherOwnersFixture() {
        long other = users.saveAndFlush(new UserEntity("other-owner")).getId();
        Document original = fixture(other, "private");
        String path = "/api/v1/documents/" + original.id();
        assertThat(http.json(http.request("GET", "/api/v1/documents", null)).size()).isZero();
        http.assertProblem(http.request("GET", path, null), 404);
        http.assertProblem(http.request("PUT", path + "/metadata", "{\"filename\":\"forged.pdf\",\"contentType\":\"application/pdf\"}"), 404);
        http.assertProblem(http.request("DELETE", path, null), 404);
        assertThat(service.getDocumentById(other, original.id())).contains(original);
    }

    @Test
    void healthIncludesStorageButLivenessDoesNot() {
        for (String path : new String[]{"/actuator/health", "/actuator/health/readiness", "/actuator/health/liveness"}) {
            var response = http.request("GET", path, null);
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(http.json(response).get("status").asString()).isEqualTo("UP");
            assertThat(http.json(response).has("components")).isFalse();
        }
        when(storage.available()).thenReturn(false);
        assertThat(http.request("GET", "/actuator/health/readiness", null).getStatusCode().value()).isEqualTo(503);
        assertThat(http.request("GET", "/actuator/health/liveness", null).getStatusCode().value()).isEqualTo(200);
    }

    private Document fixture(long ownerId, String name) {
        return service.createStoredDocument(ownerId, new DocumentMetadata(name + ".pdf", "application/pdf", "Example", name),
                "documents/" + name + ".pdf");
    }
}
