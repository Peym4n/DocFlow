package at.fhtw.docflow.document.application;

import at.fhtw.docflow.document.domain.Document;
import at.fhtw.docflow.document.domain.DocumentMetadata;
import java.util.List;
import java.util.Optional;

public interface DocumentUseCases {
    Document createDocument(DocumentMetadata metadata);
    List<Document> getAllDocuments();
    Optional<Document> getDocumentById(Long id);
    Optional<Document> updateDocument(Long id, DocumentMetadata metadata);
    boolean deleteDocument(Long id);
}
