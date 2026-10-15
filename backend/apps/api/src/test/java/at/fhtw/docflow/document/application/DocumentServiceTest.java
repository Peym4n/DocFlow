package at.fhtw.docflow.document.application;

import at.fhtw.docflow.document.domain.Document;
import at.fhtw.docflow.document.domain.DocumentMetadata;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {
    private static final long OWNER = 7L;
    @Mock
    private DocumentStore store;
    @InjectMocks
    private DocumentService service;

    @Test
    void createDocumentShouldSaveWithoutClientIdentity() {
        DocumentMetadata metadata = new DocumentMetadata("invoice.pdf", "application/pdf", "Invoice", "Invoice title");
        Document newDocument = Document.create(OWNER, metadata);
        Document saved = new Document(1L, metadata.filename(), metadata.contentType(), metadata.description(),
                LocalDateTime.of(2026, 10, 14, 12, 0), OWNER, metadata.title(), null);
        when(store.save(newDocument)).thenReturn(saved);

        assertEquals(saved, service.createDocument(OWNER, metadata));
        verify(store).save(newDocument);
        assertEquals(OWNER, newDocument.ownerId());
        assertNull(newDocument.id());
        assertNull(newDocument.createdAt());
        assertNull(newDocument.originalObjectKey());
    }

    @Test
    void getAllDocumentsShouldReturnDocumentsForOwnerOnly() {
        List<Document> documents = List.of(document(1L), document(2L));
        when(store.findAllByOwnerId(OWNER)).thenReturn(documents);
        assertEquals(documents, service.getAllDocuments(OWNER));
    }

    @Test
    void getDocumentByIdShouldReturnDocument() {
        Document document = document(1L);
        when(store.findByIdAndOwnerId(1L, OWNER)).thenReturn(Optional.of(document));
        assertEquals(Optional.of(document), service.getDocumentById(OWNER, 1L));
    }

    @Test
    void getDocumentByIdShouldReturnEmptyWhenNotFoundOrNotOwned() {
        when(store.findByIdAndOwnerId(999L, OWNER)).thenReturn(Optional.empty());
        assertTrue(service.getDocumentById(OWNER, 999L).isEmpty());
    }

    @Test
    void updateDocumentShouldPreserveProtectedFieldsAndClearDescription() {
        Document existing = document(1L);
        DocumentMetadata replacement = new DocumentMetadata("renamed.pdf", "application/pdf", null, "New title");
        Document updated = existing.withMetadata(replacement);
        when(store.findByIdAndOwnerId(1L, OWNER)).thenReturn(Optional.of(existing));
        when(store.save(updated)).thenReturn(updated);

        Document result = service.updateDocument(OWNER, 1L, replacement).orElseThrow();
        assertEquals(existing.id(), result.id());
        assertEquals(existing.createdAt(), result.createdAt());
        assertEquals(existing.ownerId(), result.ownerId());
        assertEquals(existing.originalObjectKey(), result.originalObjectKey());
        assertEquals("New title", result.title());
        assertEquals("renamed.pdf", result.filename());
        assertNull(result.description());
        assertEquals("Original", existing.description());
        verify(store).save(updated);
    }

    @Test
    void updateDocumentShouldNotSaveWhenNotFoundOrNotOwned() {
        when(store.findByIdAndOwnerId(999L, OWNER)).thenReturn(Optional.empty());
        assertTrue(service.updateDocument(OWNER, 999L,
                new DocumentMetadata("invoice.pdf", "application/pdf", null)).isEmpty());
        verify(store, never()).save(any());
    }

    @Test
    void deleteDocumentShouldDeleteOwnedDocument() {
        when(store.deleteByIdAndOwnerId(1L, OWNER)).thenReturn(true);
        assertTrue(service.deleteDocument(OWNER, 1L));
        verify(store).deleteByIdAndOwnerId(1L, OWNER);
    }

    @Test
    void deleteDocumentShouldReturnFalseWhenNotFoundOrNotOwned() {
        when(store.deleteByIdAndOwnerId(999L, OWNER)).thenReturn(false);
        assertFalse(service.deleteDocument(OWNER, 999L));
    }

    private Document document(long id) {
        return new Document(id, "invoice.pdf", "application/pdf", "Original",
                LocalDateTime.of(2026, 10, 14, 12, 0), OWNER, "Invoice", "documents/original.pdf");
    }
}
