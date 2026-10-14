package at.fhtw.docflow.document.api;

import at.fhtw.docflow.document.api.DocumentRequest;
import at.fhtw.docflow.document.api.DocumentResponse;
import at.fhtw.docflow.document.persistence.Document;
import at.fhtw.docflow.document.api.DocumentMapper;
import at.fhtw.docflow.document.application.DocumentService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse createDocument(@Valid @RequestBody DocumentRequest request) {
        Document document = DocumentMapper.toEntity(request);

        Document savedDocument = documentService.createDocument(document);

        return DocumentMapper.toResponse(savedDocument);
    }

    @GetMapping
    public List<DocumentResponse> getAllDocuments() {
        return documentService.getAllDocuments()
                .stream()
                .map(DocumentMapper::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> getDocumentById(@PathVariable Long id) {
        return documentService.getDocumentById(id)
                .map(DocumentMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<DocumentResponse> updateDocument(
            @PathVariable Long id,
            @Valid @RequestBody DocumentRequest request) {

        Document updatedDocument = DocumentMapper.toEntity(request);

        return documentService.updateDocument(id, updatedDocument)
                .map(DocumentMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long id) {
        if (documentService.deleteDocument(id)) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.notFound().build();
    }
}