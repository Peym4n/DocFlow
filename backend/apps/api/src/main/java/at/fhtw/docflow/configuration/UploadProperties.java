package at.fhtw.docflow.configuration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("docflow.upload")
public record UploadProperties(@Min(1) @Max(104857600) int maxBytes, @Min(1) @Max(1000) int maxPages) {
}
