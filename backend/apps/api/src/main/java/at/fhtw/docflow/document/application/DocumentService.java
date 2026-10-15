package at.fhtw.docflow.document.application;

import at.fhtw.docflow.document.domain.Document;
import at.fhtw.docflow.document.domain.DocumentMetadata;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class DocumentService implements DocumentUseCases {
    private final DocumentStore documentStore;

    public DocumentService(DocumentStore documentStore) {
        this.documentStore = documentStore;
    }

    @Override
    @Transactional
    public Document createDocument(long ownerId, DocumentMetadata metadata) {
        return documentStore.save(Document.create(ownerId, metadata));
    }

    @Override
    @Transactional
    public Document createStoredDocument(long ownerId, DocumentMetadata metadata, String originalObjectKey) {
        return documentStore.save(Document.create(ownerId, metadata, originalObjectKey));
    }

    @Override
    public List<Document> getAllDocuments(long ownerId) {
        return documentStore.findAllByOwnerId(ownerId);
    }

    @Override
    public Optional<Document> getDocumentById(long ownerId, Long id) {
        return documentStore.findByIdAndOwnerId(id, ownerId);
    }

    @Override
    @Transactional
    public Optional<Document> updateDocument(long ownerId, Long id, DocumentMetadata metadata) {
        return documentStore.findByIdAndOwnerId(id, ownerId)
                .map(document -> documentStore.save(document.withMetadata(metadata)));
    }

    @Override
    @Transactional
    public boolean deleteDocument(long ownerId, Long id) {
        return documentStore.deleteByIdAndOwnerId(id, ownerId);
    }
}
