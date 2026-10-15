package at.fhtw.docflow.document.api;

import java.time.LocalDateTime;

public record DocumentResponse(
        Long id,
        String filename,
        String contentType,
        String description,
        LocalDateTime createdAt,
        String title
) {
}