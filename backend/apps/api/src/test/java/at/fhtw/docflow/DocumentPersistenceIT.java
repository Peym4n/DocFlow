package at.fhtw.docflow;

import at.fhtw.docflow.document.application.DocumentStore;
import at.fhtw.docflow.document.application.DocumentUseCases;
import at.fhtw.docflow.document.application.DocumentNotFoundException;
import at.fhtw.docflow.document.domain.Document;
import at.fhtw.docflow.document.domain.DocumentMetadata;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
    PlatformTransactionManager transactionManager;

    @Test
    void persistsUpdatesAndDeletesMetadataAcrossTransactions() {
        Document saved = service.createDocument(new DocumentMetadata("invoice.pdf", "application/pdf", "Invoice"));
        assertThat(saved.id()).isPositive();
        assertThat(saved.createdAt()).isNotNull();
        assertThat(service.getDocumentById(saved.id())).contains(saved);
        assertThat(service.getAllDocuments()).contains(saved);

        Document updated = service.updateDocument(saved.id(),
                new DocumentMetadata("renamed.pdf", "text/plain", null)).orElseThrow();
        assertThat(updated.id()).isEqualTo(saved.id());
        assertThat(updated.createdAt()).isEqualTo(saved.createdAt());
        assertThat(updated.filename()).isEqualTo("renamed.pdf");
        assertThat(updated.contentType()).isEqualTo("text/plain");
        assertThat(updated.description()).isNull();
        assertThat(service.getDocumentById(saved.id())).contains(updated);
        assertThat(service.deleteDocument(saved.id())).isTrue();
        assertThat(service.getDocumentById(saved.id())).isEmpty();
        assertThat(service.deleteDocument(saved.id())).isFalse();
        assertThat(service.updateDocument(saved.id(), new DocumentMetadata("missing.pdf", "application/pdf", null))).isEmpty();
        assertThatThrownBy(() -> store.save(updated)).isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    void rollsBackFlushedCreateUpdateAndDeleteWhenTransactionFails() {
        Document original = service.createDocument(new DocumentMetadata("original.pdf", "application/pdf", "Keep"));
        long count = service.getAllDocuments().size();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            service.createDocument(new DocumentMetadata("rollback.pdf", "application/pdf", null));
            service.updateDocument(original.id(), new DocumentMetadata("changed.pdf", "application/pdf", null));
            service.deleteDocument(original.id());
            assertThat(service.getDocumentById(original.id())).isEmpty();
            throw new IllegalStateException("Force rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(service.getAllDocuments()).hasSize((int) count);
        assertThat(service.getDocumentById(original.id())).contains(original);
        service.deleteDocument(original.id());
    }
}
