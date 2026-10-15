package at.fhtw.docflow.document.application;

import at.fhtw.docflow.document.domain.Document;
import at.fhtw.docflow.document.domain.DocumentMetadata;
import java.util.List;
import java.util.Optional;

public interface DocumentUseCases {
    Document createDocument(long ownerId, DocumentMetadata metadata);
    List<Document> getAllDocuments(long ownerId);
    Optional<Document> getDocumentById(long ownerId, Long id);
    Optional<Document> updateDocument(long ownerId, Long id, DocumentMetadata metadata);
    boolean deleteDocument(long ownerId, Long id);
}
