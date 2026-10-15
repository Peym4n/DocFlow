package at.fhtw.docflow.document.api;

import at.fhtw.docflow.configuration.UploadProperties;
import at.fhtw.docflow.document.application.DocumentFileUseCases;
import at.fhtw.docflow.document.application.DocumentNotFoundException;
import at.fhtw.docflow.document.application.DocumentUseCases;
import at.fhtw.docflow.document.application.InvalidPdfException;
import at.fhtw.docflow.document.application.UploadTooLargeException;
import at.fhtw.docflow.document.domain.DocumentMetadata;
import at.fhtw.docflow.identity.application.CurrentOwner;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/documents")
@Tag(name = "Documents")
public class DocumentFileController {
    private final DocumentFileUseCases files;
    private final DocumentUseCases documents;
    private final CurrentOwner currentOwner;
    private final UploadProperties limits;

    public DocumentFileController(DocumentFileUseCases files, DocumentUseCases documents,
                                  CurrentOwner currentOwner, UploadProperties limits) {
        this.files = files;
        this.documents = documents;
        this.currentOwner = currentOwner;
        this.limits = limits;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Upload a PDF", description = "Multipart fields: file, optional title and description. Processing is deferred, not queued.")
    @ApiResponse(responseCode = "202", description = "PDF stored; processing deferred",
            headers = @Header(name = "Location", description = "Processing status resource", schema = @Schema(type = "string", format = "uri-reference")),
            content = @Content(schema = @Schema(implementation = DocumentUploadResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid PDF or metadata",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "413", description = "Upload exceeds configured limits",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<DocumentUploadResponse> upload(@Valid @ModelAttribute DocumentRequest request) {
        long ownerId = currentOwner.requireOwnerId();
        var file = request.file();
        if (file.getSize() > limits.maxBytes()) {
            throw new UploadTooLargeException();
        }
        String filename = file.getOriginalFilename();
        if (filename == null || filename.chars().anyMatch(Character::isISOControl)) {
            throw new InvalidPdfException("A valid PDF filename is required");
        }
        filename = filename.replace('\\', '/');
        filename = filename.substring(filename.lastIndexOf('/') + 1);
        DocumentMetadata metadata = new DocumentMetadata(filename, file.getContentType(), request.description(), request.title());
        byte[] bytes;
        try (var stream = file.getInputStream()) {
            bytes = stream.readNBytes(limits.maxBytes() + 1);
        } catch (IOException exception) {
            throw new InvalidPdfException("Unable to read the uploaded PDF");
        }
        if (bytes.length > limits.maxBytes()) {
            throw new UploadTooLargeException();
        }
        var saved = files.upload(ownerId, metadata, bytes);
        String statusUrl = "/api/v1/documents/" + saved.id() + "/processing";
        return ResponseEntity.accepted().location(URI.create(statusUrl))
                .body(new DocumentUploadResponse(saved.id(), "DEFERRED", statusUrl));
    }

    @GetMapping(value = "/{id}/content", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Download the original PDF")
    @ApiResponse(responseCode = "200", description = "Original PDF attachment",
            content = @Content(mediaType = "application/pdf", schema = @Schema(type = "string", format = "binary")))
    @ApiResponse(responseCode = "404", description = "Document or content not found",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<byte[]> content(@PathVariable Long id) {
        var content = files.getContent(currentOwner.requireOwnerId(), id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).contentLength(content.bytes().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(content.filename(), StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff").body(content.bytes());
    }

    @GetMapping(value = "/{id}/processing", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get processing status", description = "PDF processing is deferred until the worker stages are implemented.")
    @ApiResponse(responseCode = "200", description = "Processing status",
            content = @Content(schema = @Schema(implementation = DocumentProcessingResponse.class)))
    @ApiResponse(responseCode = "404", description = "Document not found",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public DocumentProcessingResponse processing(@PathVariable Long id) {
        var document = documents.getDocumentById(currentOwner.requireOwnerId(), id)
                .orElseThrow(() -> new DocumentNotFoundException(id));
        return new DocumentProcessingResponse(document.id(), "DEFERRED", "PDF stored; processing is not queued yet");
    }
}
