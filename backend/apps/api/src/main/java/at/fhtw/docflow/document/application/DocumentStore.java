package at.fhtw.docflow.document.application;

import at.fhtw.docflow.document.domain.Document;
import java.util.List;
import java.util.Optional;

public interface DocumentStore {
    Document save(Document document);
    List<Document> findAllByOwnerId(long ownerId);
    Optional<Document> findByIdAndOwnerId(Long id, long ownerId);
    boolean deleteByIdAndOwnerId(Long id, long ownerId);
}
