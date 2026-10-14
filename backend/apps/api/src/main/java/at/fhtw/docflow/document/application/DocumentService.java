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
    public Document createDocument(DocumentMetadata metadata) {
        return documentStore.save(Document.create(metadata));
    }

    @Override
    public List<Document> getAllDocuments() {
        return documentStore.findAll();
    }

    @Override
    public Optional<Document> getDocumentById(Long id) {
        return documentStore.findById(id);
    }

    @Override
    @Transactional
    public Optional<Document> updateDocument(Long id, DocumentMetadata metadata) {
        return documentStore.findById(id)
                .map(document -> documentStore.save(document.withMetadata(metadata)));
    }

    @Override
    @Transactional
    public boolean deleteDocument(Long id) {
        if (!documentStore.existsById(id)) {
            return false;
        }
        documentStore.deleteById(id);
        return true;
    }
}
