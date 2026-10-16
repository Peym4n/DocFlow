package com.docflow.backend.mapper;

import com.docflow.backend.dto.DocumentTypeRequest;
import com.docflow.backend.dto.DocumentTypeResponse;
import com.docflow.backend.entity.DocumentType;
import org.springframework.stereotype.Component;

@Component
public class DocumentTypeMapper {

    public DocumentType toEntity(DocumentTypeRequest request) {
        return new DocumentType(
                request.name(),
                request.description()
        );
    }

    public DocumentTypeResponse toResponse(DocumentType documentType) {
        return new DocumentTypeResponse(
                documentType.getId(),
                documentType.getName(),
                documentType.getDescription()
                );
    }
}
