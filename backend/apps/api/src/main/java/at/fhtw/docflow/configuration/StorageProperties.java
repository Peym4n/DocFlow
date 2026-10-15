package at.fhtw.docflow.configuration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("docflow.storage")
public record StorageProperties(@NotBlank String endpoint, @NotBlank String bucket,
                                @NotBlank String accessKey, @NotBlank String secretKey,
                                @Min(100) @Max(60000) int timeoutMillis) {
    @Override
    public String toString() {
        return "StorageProperties[credentials redacted]";
    }
}
