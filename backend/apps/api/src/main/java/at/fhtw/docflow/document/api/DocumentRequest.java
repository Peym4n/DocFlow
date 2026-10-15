package at.fhtw.docflow.document.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Setup-only metadata creation", additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record DocumentRequest(

        @NotBlank
        @Size(max = 255)
        String filename,

        @NotBlank
        @Size(max = 255)
        String contentType,

        @Size(max = 255)
        @Schema(nullable = true)
        String description,

        @Size(max = 255)
        @Schema(description = "Document title; defaults to filename when omitted")
        String title

) {
}