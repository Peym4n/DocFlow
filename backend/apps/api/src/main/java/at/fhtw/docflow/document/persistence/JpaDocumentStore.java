package at.fhtw.docflow.document.persistence;

import at.fhtw.docflow.document.application.DocumentStore;
import at.fhtw.docflow.document.application.DocumentNotFoundException;
import at.fhtw.docflow.document.domain.Document;
import at.fhtw.docflow.identity.persistence.UserRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public class JpaDocumentStore implements DocumentStore {
    private final DocumentRepository repository;
    private final DocumentPersistenceMapper mapper;
    private final UserRepository users;

    public JpaDocumentStore(DocumentRepository repository, DocumentPersistenceMapper mapper, UserRepository users) {
        this.repository = repository;
        this.mapper = mapper;
        this.users = users;
    }

    @Override
    public Document save(Document document) {
        DocumentEntity entity;
        if (document.id() == null) {
            entity = mapper.toNewEntity(document);
            entity.setOwner(users.getReferenceById(document.ownerId()));
        } else {
            entity = repository.findByIdAndOwnerId(document.id(), document.ownerId())
                    .orElseThrow(() -> new DocumentNotFoundException(document.id()));
            mapper.updateMetadata(document, entity);
        }
        return mapper.toDomain(repository.saveAndFlush(entity));
    }

    @Override
    public List<Document> findAllByOwnerId(long ownerId) {
        return repository.findAllByOwnerId(ownerId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<Document> findByIdAndOwnerId(Long id, long ownerId) {
        return repository.findByIdAndOwnerId(id, ownerId).map(mapper::toDomain);
    }

    @Override
    public boolean deleteByIdAndOwnerId(Long id, long ownerId) {
        return repository.deleteOwnedDocument(id, ownerId) > 0;
    }
}
