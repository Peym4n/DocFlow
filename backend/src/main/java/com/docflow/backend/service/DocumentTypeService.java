package com.docflow.backend.service;

import com.docflow.backend.dto.DocumentTypeRequest;
import com.docflow.backend.dto.DocumentTypeResponse;
import com.docflow.backend.entity.DocumentType;
import com.docflow.backend.mapper.DocumentTypeMapper;
import com.docflow.backend.repository.DocumentTypeRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DocumentTypeService {

    private final DocumentTypeRepository repository;
    private final DocumentTypeMapper mapper;

    public DocumentTypeService(DocumentTypeRepository repository, DocumentTypeMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public DocumentTypeResponse create(DocumentTypeRequest request) {
        DocumentType documentType = mapper.toEntity(request);

        DocumentType saved = repository.save(documentType);

        return mapper.toResponse(saved);
    }

    public List<DocumentTypeResponse> findAll() {
        return repository.findAll()
                .stream()
                .map(mapper::toResponse)
                .toList();
    }
}
