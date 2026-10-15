package at.fhtw.docflow.document.api;

public record DocumentProcessingResponse(Long documentId, String status, String detail) {
}
