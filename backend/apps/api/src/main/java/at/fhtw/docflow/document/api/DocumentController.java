package at.fhtw.docflow.document.api;

import at.fhtw.docflow.document.application.DocumentNotFoundException;
import at.fhtw.docflow.document.application.DocumentFileUseCases;
import at.fhtw.docflow.document.application.DocumentUseCases;
import at.fhtw.docflow.identity.application.CurrentOwner;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/v1/documents", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Documents", description = "Owner-scoped document metadata and PDF storage")
public class DocumentController {
    private final DocumentUseCases documentService;
    private final DocumentMapper documentMapper;
    private final CurrentOwner currentOwner;
    private final DocumentFileUseCases files;

    public DocumentController(DocumentUseCases documentService, DocumentMapper documentMapper, CurrentOwner currentOwner,
                              DocumentFileUseCases files) {
        this.documentService = documentService;
        this.documentMapper = documentMapper;
        this.currentOwner = currentOwner;
        this.files = files;
    }

    @GetMapping
    @Operation(summary = "List document metadata", description = "Unpaginated list belonging to the current owner.")
    public List<DocumentResponse> getAllDocuments() {
        return documentService.getAllDocuments(currentOwner.requireOwnerId()).stream().map(documentMapper::toResponse).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get document metadata")
    @ApiResponse(responseCode = "200", description = "Document metadata")
    @ApiResponse(responseCode = "404", description = "Document not found",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public DocumentResponse getDocumentById(@PathVariable Long id) {
        return documentService.getDocumentById(currentOwner.requireOwnerId(), id).map(documentMapper::toResponse)
                .orElseThrow(() -> new DocumentNotFoundException(id));
    }

    @PutMapping(value = "/{id}/metadata", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Replace editable metadata", description = "Owner, ID, creation time and original storage key are preserved.")
    @ApiResponse(responseCode = "200", description = "Updated metadata")
    @ApiResponse(responseCode = "400", description = "Invalid metadata",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Document not found",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public DocumentResponse updateDocument(@PathVariable Long id, @Valid @RequestBody DocumentMetadataRequest request) {
        return documentService.updateDocument(currentOwner.requireOwnerId(), id, documentMapper.toMetadata(request)).map(documentMapper::toResponse)
                .orElseThrow(() -> new DocumentNotFoundException(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a document", description = "Commits metadata deletion, then attempts object cleanup.")
    @ApiResponse(responseCode = "204", description = "Document deleted", content = @Content)
    @ApiResponse(responseCode = "404", description = "Document not found",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<Void> deleteDocument(@PathVariable Long id) {
        if (!files.deleteDocument(currentOwner.requireOwnerId(), id)) {
            throw new DocumentNotFoundException(id);
        }
        return ResponseEntity.noContent().build();
    }
}
