package at.fhtw.docflow.document.domain;

import java.time.LocalDateTime;

public record Document(Long id, String filename, String contentType, String description,
                       LocalDateTime createdAt) {
    public Document {
        new DocumentMetadata(filename, contentType, description);
    }

    public static Document create(DocumentMetadata metadata) {
        return new Document(null, metadata.filename(), metadata.contentType(), metadata.description(), null);
    }

    public Document withMetadata(DocumentMetadata metadata) {
        return new Document(id, metadata.filename(), metadata.contentType(), metadata.description(), createdAt);
    }
}
