package at.fhtw.docflow.document.api;

import at.fhtw.docflow.document.application.DocumentNotFoundException;
import at.fhtw.docflow.document.application.DocumentUseCases;
import at.fhtw.docflow.document.domain.DocumentMetadata;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/v1/documents", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Documents", description = "Setup metadata operations; file upload is pending")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Invalid request",
                content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "409", description = "Stored data conflict",
                content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "503", description = "Database unavailable",
                content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
})
public class DocumentController {
    private final DocumentUseCases documentService;
    private final DocumentMapper documentMapper;

    public DocumentController(DocumentUseCases documentService, DocumentMapper documentMapper) {
        this.documentService = documentService;
        this.documentMapper = documentMapper;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a metadata record",
            description = "Setup-only JSON operation: does not upload a PDF or start processing.")
    @ApiResponse(responseCode = "201", description = "Metadata created",
            headers = @Header(name = "Location", description = "Created metadata resource", schema = @Schema(type = "string", format = "uri-reference")),
            content = @Content(schema = @Schema(implementation = DocumentResponse.class)))
    public ResponseEntity<DocumentResponse> createDocument(@Valid @RequestBody DocumentRequest request) {
        DocumentMetadata metadata = documentMapper.toMetadata(request);
        DocumentResponse saved = documentMapper.toResponse(documentService.createDocument(metadata));
        return ResponseEntity.created(URI.create("/api/v1/documents/" + saved.id())).body(saved);
    }

    @GetMapping
    @Operation(summary = "List metadata records", description = "Scaffold returns an unpaginated list.")
    @ApiResponse(responseCode = "200", description = "Metadata records",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = DocumentResponse.class))))
    public List<DocumentResponse> getAllDocuments() {
        return documentService.getAllDocuments().stream().map(documentMapper::toResponse).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get document metadata")
    @ApiResponse(responseCode = "404", description = "Document not found",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "200", description = "Document metadata",
            content = @Content(schema = @Schema(implementation = DocumentResponse.class)))
    public DocumentResponse getDocumentById(@PathVariable Long id) {
        return documentService.getDocumentById(id).map(documentMapper::toResponse)
                .orElseThrow(() -> new DocumentNotFoundException(id));
    }

    @PutMapping(value = "/{id}/metadata", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Replace editable metadata",
            description = "filename and contentType are required. Null description clears it. ID and creation time are preserved; unknown fields are rejected.")
    @ApiResponse(responseCode = "404", description = "Document not found",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "200", description = "Updated metadata",
            content = @Content(schema = @Schema(implementation = DocumentResponse.class)))
    public DocumentResponse updateDocument(@PathVariable Long id, @Valid @RequestBody DocumentMetadataRequest request) {
        return documentService.updateDocument(id, documentMapper.toMetadata(request)).map(documentMapper::toResponse)
                .orElseThrow(() -> new DocumentNotFoundException(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a metadata record")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Metadata deleted", content = @Content),
            @ApiResponse(responseCode = "404", description = "Document not found",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<Void> deleteDocument(@PathVariable Long id) {
        if (!documentService.deleteDocument(id)) {
            throw new DocumentNotFoundException(id);
        }
        return ResponseEntity.noContent().build();
    }
}
