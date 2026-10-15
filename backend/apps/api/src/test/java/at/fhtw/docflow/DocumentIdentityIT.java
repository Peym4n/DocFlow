package at.fhtw.docflow;

import at.fhtw.docflow.identity.persistence.UserRepository;
import java.time.Duration;
import java.net.http.HttpClient;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DocumentIdentityIT extends PostgresIntegrationTest {
    @LocalServerPort
    int port;
    @Autowired
    UserRepository users;

    @Test
    void normalModeDoesNotSeedDemoOwnerOrTrustClientOwnerHeaders() {
        assertThat(users.findByUsername("demo")).isEmpty();
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(15));
        var client = RestClient.builder().baseUrl("http://localhost:" + port).requestFactory(factory)
                .defaultHeader("X-Owner-Id", "1").defaultHeader("X-User", "demo").build();
        var form = new LinkedMultiValueMap<String, Object>();
        form.add("file", new ByteArrayResource(new byte[]{1}) {
            @Override
            public String getFilename() {
                return "test.pdf";
            }
        });
        for (HttpMethod method : new HttpMethod[]{HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE}) {
            String path = method == HttpMethod.PUT ? "/api/v1/documents/1/metadata"
                    : method == HttpMethod.DELETE ? "/api/v1/documents/1" : "/api/v1/documents";
            var request = client.method(method).uri(path);
            if (method == HttpMethod.POST) {
                request.contentType(MediaType.MULTIPART_FORM_DATA).body(form);
            } else if (method == HttpMethod.PUT) {
                request.contentType(MediaType.APPLICATION_JSON).body("{\"filename\":\"file.pdf\",\"contentType\":\"application/pdf\"}");
            }
            request.exchange((sent, response) -> {
                assertThat(response.getStatusCode().value()).isEqualTo(401);
                assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
                assertThat(response.bodyTo(Map.class).get("status")).isEqualTo(401);
                return null;
            });
        }
    }
}
