package at.fhtw.docflow.document.application;

import at.fhtw.docflow.document.domain.Document;
import at.fhtw.docflow.document.domain.DocumentMetadata;

public interface DocumentFileUseCases {
    Document upload(long ownerId, DocumentMetadata metadata, byte[] content);
    DocumentContent getContent(long ownerId, Long id);
    boolean deleteDocument(long ownerId, Long id);
}
