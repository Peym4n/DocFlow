package com.docflow.backend.controller;


import com.docflow.backend.dto.DocumentTypeRequest;
import com.docflow.backend.dto.DocumentTypeResponse;
import com.docflow.backend.entity.DocumentType;
import com.docflow.backend.service.DocumentTypeService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/document-types")
public class DocumentTypeController {

    private final DocumentTypeService service;

    public DocumentTypeController(DocumentTypeService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentTypeResponse create(
            @RequestBody DocumentTypeRequest request) {

        return service.create(request);
    }

    @GetMapping
    public List<DocumentTypeResponse> findAll() {
        return service.findAll();
    }
}
