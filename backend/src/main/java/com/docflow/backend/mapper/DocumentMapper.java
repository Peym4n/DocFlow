package com.docflow.backend.mapper;

import com.docflow.backend.dto.DocumentRequest;
import com.docflow.backend.dto.DocumentResponse;
import com.docflow.backend.entity.Document;

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