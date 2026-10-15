package at.fhtw.docflow;

import java.time.Duration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

final class TestHttpClient {
    private final RestClient client;
    private final JsonMapper mapper = JsonMapper.builder().build();

    TestHttpClient(int port) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));
        client = RestClient.builder().baseUrl("http://localhost:" + port).requestFactory(factory).build();
    }

    ResponseEntity<byte[]> request(String method, String path, String body) {
        var request = client.method(HttpMethod.valueOf(method)).uri(path);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        return exchange(request);
    }

    ResponseEntity<byte[]> upload(MultiValueMap<String, Object> form) {
        return exchange(client.post().uri("/api/v1/documents").contentType(MediaType.MULTIPART_FORM_DATA).body(form));
    }

    JsonNode json(ResponseEntity<byte[]> response) {
        return mapper.readTree(response.getBody());
    }

    void assertProblem(ResponseEntity<byte[]> response, int status) {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(json(response).get("status").asInt()).isEqualTo(status);
    }

    private ResponseEntity<byte[]> exchange(RestClient.RequestHeadersSpec<?> request) {
        return request.exchange((sent, response) -> new ResponseEntity<>(
                response.bodyTo(byte[].class), response.getHeaders(), response.getStatusCode()));
    }
}
