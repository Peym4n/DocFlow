package at.fhtw.docflow;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DocumentApiIT extends PostgresIntegrationTest {
    @LocalServerPort
    int port;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void runningApiSupportsMetadataLifecycle() throws Exception {
        HttpResponse<String> created = request("POST", "/api/v1/documents",
                "{\"filename\":\"invoice.pdf\",\"contentType\":\"application/pdf\",\"description\":\"Invoice\"}");
        assertThat(created.statusCode()).isEqualTo(201);
        JsonNode original = mapper.readTree(created.body());
        String location = created.headers().firstValue("Location").orElseThrow();
        assertThat(location).isEqualTo("/api/v1/documents/" + original.get("id").asLong());
        assertThat(original.get("createdAt").asString()).isNotBlank();
        assertThat(mapper.readTree(request("GET", location, null).body())).isEqualTo(original);
        HttpResponse<String> listed = request("GET", "/api/v1/documents", null);
        assertThat(listed.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(listed.body()).get(0)).isEqualTo(original);

        HttpResponse<String> updated = request("PUT", location + "/metadata",
                "{\"filename\":\"renamed.pdf\",\"contentType\":\"application/pdf\",\"description\":null}");
        assertThat(updated.statusCode()).isEqualTo(200);
        JsonNode replacement = mapper.readTree(updated.body());
        assertThat(replacement.get("id")).isEqualTo(original.get("id"));
        assertThat(replacement.get("createdAt")).isEqualTo(original.get("createdAt"));
        assertThat(replacement.get("filename").asString()).isEqualTo("renamed.pdf");
        assertThat(replacement.get("description").isNull()).isTrue();
        assertThat(mapper.readTree(request("GET", location, null).body())).isEqualTo(replacement);
        HttpResponse<String> deleted = request("DELETE", location, null);
        assertThat(deleted.statusCode()).isEqualTo(204);
        assertThat(deleted.body()).isEmpty();
        assertProblem(request("GET", location, null), 404);
        assertProblem(request("DELETE", location, null), 404);
        assertProblem(request("PUT", location + "/metadata",
                "{\"filename\":\"missing.pdf\",\"contentType\":\"application/pdf\"}"), 404);
    }

    @Test
    void runningApiRejectsInvalidAndProtectedFields() throws Exception {
        assertProblem(request("POST", "/api/v1/documents", "{}"), 400);
        assertProblem(request("POST", "/api/v1/documents", "{"), 400);
        assertProblem(request("POST", "/api/v1/documents",
                "{\"filename\":\"invoice.pdf\",\"contentType\":\"application/pdf\",\"id\":99}"), 400);
        assertProblem(request("PUT", "/api/v1/documents/1/metadata", "{}"), 400);
        assertProblem(request("GET", "/api/v1/documents/invalid", null), 400);
        assertProblem(request("PUT", "/api/v1/documents/1", "{}"), 405);
        assertThat(mapper.readTree(request("GET", "/api/v1/documents", null).body()).size()).isZero();
    }

    @Test
    void exposesOpenApiAndDependencyAwareHealth() throws Exception {
        HttpResponse<String> docs = request("GET", "/v3/api-docs", null);
        assertThat(docs.statusCode()).isEqualTo(200);
        JsonNode paths = mapper.readTree(docs.body()).get("paths");
        assertThat(paths.has("/api/v1/documents")).isTrue();
        assertThat(paths.get("/api/v1/documents/{id}/metadata").has("put")).isTrue();
        for (String path : new String[]{"/actuator/health", "/actuator/health/readiness", "/actuator/health/liveness"}) {
            HttpResponse<String> health = request("GET", path, null);
            assertThat(health.statusCode()).isEqualTo(200);
            assertThat(mapper.readTree(health.body()).get("status").asString()).isEqualTo("UP");
            assertThat(mapper.readTree(health.body()).has("components")).isFalse();
        }
    }

    private void assertProblem(HttpResponse<String> response, int status) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/problem+json");
        assertThat(mapper.readTree(response.body()).get("status").asInt()).isEqualTo(status);
    }

    private HttpResponse<String> request(String method, String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
