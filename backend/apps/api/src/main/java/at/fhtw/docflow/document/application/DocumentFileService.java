package at.fhtw.docflow.document.application;

import at.fhtw.docflow.document.domain.Document;
import at.fhtw.docflow.document.domain.DocumentMetadata;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class DocumentFileService implements DocumentFileUseCases {
    private static final Logger log = LoggerFactory.getLogger(DocumentFileService.class);
    private final DocumentUseCases documents;
    private final DocumentStorage storage;
    private final PdfValidator validator;

    public DocumentFileService(DocumentUseCases documents, DocumentStorage storage, PdfValidator validator) {
        this.documents = documents;
        this.storage = storage;
        this.validator = validator;
    }

    @Override
    public Document upload(long ownerId, DocumentMetadata metadata, byte[] content) {
        if (!metadata.contentType().equals("application/pdf")) {
            throw new InvalidPdfException("Only application/pdf uploads are supported");
        }
        validator.validate(content);
        String key = "documents/" + UUID.randomUUID() + ".pdf";
        try {
            storage.put(key, content);
            return documents.createStoredDocument(ownerId, metadata, key);
        } catch (RuntimeException exception) {
            cleanup(key);
            throw exception;
        }
    }

    @Override
    public DocumentContent getContent(long ownerId, Long id) {
        Document document = documents.getDocumentById(ownerId, id)
                .orElseThrow(() -> new DocumentNotFoundException(id));
        if (document.originalObjectKey() == null) {
            throw new DocumentContentNotFoundException();
        }
        return new DocumentContent(document.filename(), storage.get(document.originalObjectKey()));
    }

    @Override
    public boolean deleteDocument(long ownerId, Long id) {
        var document = documents.getDocumentById(ownerId, id);
        if (document.isEmpty() || !documents.deleteDocument(ownerId, id)) {
            return false;
        }
        if (document.get().originalObjectKey() != null) {
            cleanup(document.get().originalObjectKey());
        }
        return true;
    }

    private void cleanup(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException exception) {
            log.error("Document object cleanup failed key={} category={}; manual cleanup required", key,
                    exception.getClass().getSimpleName());
        }
    }
}
