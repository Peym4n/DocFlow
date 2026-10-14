package at.fhtw.docflow.document.api;

import at.fhtw.docflow.document.api.DocumentRequest;
import at.fhtw.docflow.document.api.DocumentResponse;
import at.fhtw.docflow.document.persistence.Document;

public class DocumentMapper {

    public static Document toEntity(DocumentRequest request) {
        return new Document(
                request.filename(),
                request.contentType(),
                request.description()
        );
    }

    public static DocumentResponse toResponse(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getFilename(),
                document.getContentType(),
                document.getDescription(),
                document.getCreatedAt()
        );
    }
}