package at.fhtw.docflow.document.application;

import at.fhtw.docflow.document.domain.Document;
import java.util.List;
import java.util.Optional;

public interface DocumentStore {
    Document save(Document document);
    List<Document> findAll();
    Optional<Document> findById(Long id);
    boolean existsById(Long id);
    void deleteById(Long id);
}
