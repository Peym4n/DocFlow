package at.fhtw.docflow;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import at.fhtw.docflow.identity.persistence.UserRepository;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DocumentIdentityIT extends PostgresIntegrationTest {
    @LocalServerPort
    int port;
    @Autowired
    UserRepository users;

    @Test
    void normalModeDoesNotSeedDemoOwnerOrTrustClientOwnerHeaders() throws Exception {
        assertThat(users.findByUsername("demo")).isEmpty();
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        for (String method : new String[]{"GET", "POST", "PUT", "DELETE"}) {
            String path = switch (method) {
                case "PUT" -> "/api/v1/documents/1/metadata";
                case "DELETE" -> "/api/v1/documents/1";
                default -> "/api/v1/documents";
            };
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("X-Owner-Id", "1")
                    .header("X-User", "demo")
                    .method(method, HttpRequest.BodyPublishers.ofString("{\"filename\":\"file.pdf\",\"contentType\":\"application/pdf\"}"))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/problem+json");
            assertThat(JsonMapper.builder().build().readTree(response.body()).get("status").asInt()).isEqualTo(401);
        }
    }
}
