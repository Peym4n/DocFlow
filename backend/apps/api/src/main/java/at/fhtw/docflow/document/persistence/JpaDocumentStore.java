package at.fhtw.docflow.document.persistence;

import at.fhtw.docflow.document.application.DocumentStore;
import at.fhtw.docflow.document.domain.Document;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public class JpaDocumentStore implements DocumentStore {
    private final DocumentRepository repository;
    private final DocumentPersistenceMapper mapper;

    public JpaDocumentStore(DocumentRepository repository, DocumentPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Document save(Document document) {
        DocumentEntity entity;
        if (document.id() == null) {
            entity = mapper.toNewEntity(document);
        } else {
            entity = repository.findById(document.id())
                    .orElseThrow(() -> new IllegalStateException("Document no longer exists"));
            mapper.updateMetadata(document, entity);
        }
        return mapper.toDomain(repository.saveAndFlush(entity));
    }

    @Override
    public List<Document> findAll() {
        return repository.findAll().stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<Document> findById(Long id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public boolean existsById(Long id) {
        return repository.existsById(id);
    }

    @Override
    public void deleteById(Long id) {
        repository.deleteById(id);
    }
}
