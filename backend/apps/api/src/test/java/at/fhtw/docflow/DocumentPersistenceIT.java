package at.fhtw.docflow;

import at.fhtw.docflow.document.application.DocumentStore;
import at.fhtw.docflow.document.application.DocumentUseCases;
import at.fhtw.docflow.document.application.DocumentNotFoundException;
import at.fhtw.docflow.document.domain.Document;
import at.fhtw.docflow.document.domain.DocumentMetadata;
import at.fhtw.docflow.document.persistence.DocumentRepository;
import at.fhtw.docflow.identity.persistence.UserEntity;
import at.fhtw.docflow.identity.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class DocumentPersistenceIT extends PostgresIntegrationTest {
    @Autowired
    DocumentUseCases service;
    @Autowired
    DocumentStore store;
    @Autowired
    DocumentRepository documents;
    @Autowired
    UserRepository users;
    @Autowired
    PlatformTransactionManager transactionManager;
    long alice;
    long bob;

    @BeforeEach
    void seedOwners() {
        alice = users.findByUsername("alice").orElseGet(() -> users.saveAndFlush(new UserEntity("alice"))).getId();
        bob = users.findByUsername("bob").orElseGet(() -> users.saveAndFlush(new UserEntity("bob"))).getId();
    }

    @AfterEach
    void clearDocuments() {
        documents.deleteAll();
    }

    @Test
    void persistsUpdatesAndDeletesMetadataAcrossTransactions() {
        Document saved = service.createDocument(alice, new DocumentMetadata("invoice.pdf", "application/pdf", "Invoice", "Electricity"));
        assertThat(saved.id()).isPositive();
        assertThat(saved.createdAt()).isNotNull();
        assertThat(saved.ownerId()).isEqualTo(alice);
        assertThat(saved.title()).isEqualTo("Electricity");
        assertThat(saved.originalObjectKey()).isNull();
        assertThat(service.getDocumentById(alice, saved.id())).contains(saved);
        assertThat(service.getAllDocuments(alice)).containsExactly(saved);

        Document updated = service.updateDocument(alice, saved.id(),
                new DocumentMetadata("renamed.pdf", "text/plain", null, "Updated title")).orElseThrow();
        assertThat(updated.id()).isEqualTo(saved.id());
        assertThat(updated.createdAt()).isEqualTo(saved.createdAt());
        assertThat(updated.ownerId()).isEqualTo(alice);
        assertThat(updated.title()).isEqualTo("Updated title");
        assertThat(updated.filename()).isEqualTo("renamed.pdf");
        assertThat(updated.contentType()).isEqualTo("text/plain");
        assertThat(updated.description()).isNull();
        assertThat(service.getDocumentById(alice, saved.id())).contains(updated);
        assertThat(service.deleteDocument(alice, saved.id())).isTrue();
        assertThat(users.existsById(alice)).isTrue();
        assertThat(service.getDocumentById(alice, saved.id())).isEmpty();
        assertThat(service.deleteDocument(alice, saved.id())).isFalse();
        assertThat(service.updateDocument(alice, saved.id(), new DocumentMetadata("missing.pdf", "application/pdf", null))).isEmpty();
        assertThatThrownBy(() -> store.save(updated)).isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    void isolatesTwoOwnersForListsReadsUpdatesAndDeletes() {
        Document first = service.createDocument(alice, new DocumentMetadata("alice.pdf", "application/pdf", "Private"));
        Document second = service.createDocument(bob, new DocumentMetadata("bob.pdf", "application/pdf", null));
        assertThat(service.getAllDocuments(alice)).containsExactly(first);
        assertThat(service.getAllDocuments(bob)).containsExactly(second);
        assertThat(service.getDocumentById(bob, first.id())).isEmpty();
        assertThat(service.getDocumentById(alice, second.id())).isEmpty();
        assertThat(service.updateDocument(bob, first.id(), new DocumentMetadata("forged.pdf", "text/plain", null))).isEmpty();
        assertThat(service.updateDocument(alice, second.id(), new DocumentMetadata("forged.pdf", "text/plain", null))).isEmpty();
        assertThat(service.deleteDocument(bob, first.id())).isFalse();
        assertThat(service.deleteDocument(alice, second.id())).isFalse();
        Document forged = new Document(first.id(), first.filename(), first.contentType(), null, first.createdAt(), bob, "Forged", null);
        assertThatThrownBy(() -> store.save(forged)).isInstanceOf(DocumentNotFoundException.class);
        assertThat(service.getDocumentById(alice, first.id())).contains(first);
        assertThat(service.getDocumentById(bob, second.id())).contains(second);
    }

    @Test
    void preservesOriginalObjectKeyAndOwnerOnMetadataChanges() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Document stored = transaction.execute(status -> store.save(Document.create(alice,
                new DocumentMetadata("stored.pdf", "application/pdf", null), "documents/server-generated.pdf")));
        Document updated = service.updateDocument(alice, stored.id(),
                new DocumentMetadata("renamed.pdf", "application/pdf", null, "New title")).orElseThrow();
        assertThat(updated.originalObjectKey()).isEqualTo(stored.originalObjectKey());
        assertThat(updated.ownerId()).isEqualTo(alice);
        Document forged = new Document(updated.id(), updated.filename(), updated.contentType(), null,
                updated.createdAt(), alice, updated.title(), "documents/replacement.pdf");
        Document result = transaction.execute(status -> store.save(forged));
        assertThat(result.originalObjectKey()).isEqualTo(stored.originalObjectKey());
        assertThat(service.getDocumentById(alice, stored.id())).contains(updated);
    }

    @Test
    void databaseRequiresAnExistingOwner() {
        assertThatThrownBy(() -> service.createDocument(Long.MAX_VALUE,
                new DocumentMetadata("invalid.pdf", "application/pdf", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(service.getAllDocuments(alice)).isEmpty();
    }

    @Test
    void rollsBackFlushedCreateUpdateAndDeleteWhenTransactionFails() {
        Document original = service.createDocument(alice, new DocumentMetadata("original.pdf", "application/pdf", "Keep"));
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            service.createDocument(alice, new DocumentMetadata("rollback.pdf", "application/pdf", null));
            service.updateDocument(alice, original.id(), new DocumentMetadata("changed.pdf", "application/pdf", null));
            service.deleteDocument(alice, original.id());
            assertThat(service.getDocumentById(alice, original.id())).isEmpty();
            throw new IllegalStateException("Force rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(service.getAllDocuments(alice)).containsExactly(original);
        assertThat(service.getDocumentById(alice, original.id())).contains(original);
    }
}
