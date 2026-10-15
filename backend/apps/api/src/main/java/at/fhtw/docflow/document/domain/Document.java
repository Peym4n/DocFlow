package at.fhtw.docflow.document.domain;

import java.time.LocalDateTime;

public record Document(Long id, String filename, String contentType, String description,
                       LocalDateTime createdAt, Long ownerId, String title, String originalObjectKey) {
    public Document {
        title = new DocumentMetadata(filename, contentType, description, title).title();
        if (ownerId == null || ownerId <= 0) {
            throw new IllegalArgumentException("A persisted owner is required");
        }
        if (originalObjectKey != null && (originalObjectKey.isBlank() || originalObjectKey.length() > 1024)) {
            throw new IllegalArgumentException("Invalid original object key");
        }
    }

    public static Document create(long ownerId, DocumentMetadata metadata) {
        return create(ownerId, metadata, null);
    }

    public static Document create(long ownerId, DocumentMetadata metadata, String originalObjectKey) {
        return new Document(null, metadata.filename(), metadata.contentType(), metadata.description(), null,
                ownerId, metadata.title(), originalObjectKey);
    }

    public Document withMetadata(DocumentMetadata metadata) {
        return new Document(id, metadata.filename(), metadata.contentType(), metadata.description(), createdAt,
                ownerId, metadata.title(), originalObjectKey);
    }
}
