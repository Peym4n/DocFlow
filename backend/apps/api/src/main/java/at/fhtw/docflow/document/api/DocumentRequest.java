package at.fhtw.docflow.document.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DocumentRequest(

        @NotBlank
        @Size(max = 255)
        String filename,

        @NotBlank
        @Size(max = 255)
        String contentType,

        @Size(max = 255)
        String description

) {
}