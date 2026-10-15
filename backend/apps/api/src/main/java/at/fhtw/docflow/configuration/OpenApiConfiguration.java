package at.fhtw.docflow.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean
    OpenAPI docFlowOpenApi() {
        return new OpenAPI()
                .info(new Info().title("DocFlow API").version("v1")
                        .description("Owner-scoped PDF upload and metadata management. Errors use application/problem+json: invalid input 400, missing identity 401, missing document 404, conflict 409, excessive upload 413 and unavailable dependencies 503."))
                .servers(List.of(new Server().url("/")));
    }
    @Bean
    OpenApiCustomizer problemResponseSchema() {
        return openApi -> {
            var problem = openApi.getComponents().getSchemas().get("ProblemDetail");
            if (problem != null) {
                // Spring serializes ProblemDetail extensions at the top level, not under properties.
                problem.getProperties().remove("properties");
                problem.addProperty("errors", new MapSchema().additionalProperties(new StringSchema())
                        .description("Field validation messages; present only for validation failures"));
            }
        };
    }
}
