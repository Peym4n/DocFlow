package at.fhtw.docflow.document.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

public record DocumentRequest(
        @NotNull @Schema(type = "string", format = "binary") MultipartFile file,
        @Size(max = 255) String title,
        @Size(max = 255) String description) {
}
