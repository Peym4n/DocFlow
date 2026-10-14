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
    @Mock
    private DocumentStore store;
    @InjectMocks
    private DocumentService service;

    @Test
    void createDocumentShouldSaveWithoutClientIdentity() {
        DocumentMetadata metadata = new DocumentMetadata("invoice.pdf", "application/pdf", "Invoice");
        Document newDocument = Document.create(metadata);
        Document saved = new Document(1L, metadata.filename(), metadata.contentType(), metadata.description(),
                LocalDateTime.of(2026, 10, 14, 12, 0));
        when(store.save(newDocument)).thenReturn(saved);

        assertEquals(saved, service.createDocument(metadata));
        verify(store).save(newDocument);
        assertNull(newDocument.id());
        assertNull(newDocument.createdAt());
    }

    @Test
    void getAllDocumentsShouldReturnDocuments() {
        List<Document> documents = List.of(document(1L), document(2L));
        when(store.findAll()).thenReturn(documents);
        assertEquals(documents, service.getAllDocuments());
    }

    @Test
    void getDocumentByIdShouldReturnDocument() {
        Document document = document(1L);
        when(store.findById(1L)).thenReturn(Optional.of(document));
        assertEquals(Optional.of(document), service.getDocumentById(1L));
    }

    @Test
    void getDocumentByIdShouldReturnEmptyWhenNotFound() {
        when(store.findById(999L)).thenReturn(Optional.empty());
        assertTrue(service.getDocumentById(999L).isEmpty());
    }

    @Test
    void updateDocumentShouldPreserveIdentityAndCreationTimeAndClearDescription() {
        Document existing = document(1L);
        DocumentMetadata replacement = new DocumentMetadata("renamed.pdf", "application/pdf", null);
        Document updated = existing.withMetadata(replacement);
        when(store.findById(1L)).thenReturn(Optional.of(existing));
        when(store.save(updated)).thenReturn(updated);

        Document result = service.updateDocument(1L, replacement).orElseThrow();
        assertEquals(existing.id(), result.id());
        assertEquals(existing.createdAt(), result.createdAt());
        assertEquals("renamed.pdf", result.filename());
        assertNull(result.description());
        assertEquals("Original", existing.description());
        verify(store).save(updated);
    }

    @Test
    void updateDocumentShouldNotSaveWhenNotFound() {
        when(store.findById(999L)).thenReturn(Optional.empty());
        assertTrue(service.updateDocument(999L,
                new DocumentMetadata("invoice.pdf", "application/pdf", null)).isEmpty());
        verify(store, never()).save(any());
    }

    @Test
    void deleteDocumentShouldDeleteExistingDocument() {
        when(store.existsById(1L)).thenReturn(true);
        assertTrue(service.deleteDocument(1L));
        verify(store).deleteById(1L);
    }

    @Test
    void deleteDocumentShouldNotDeleteWhenNotFound() {
        when(store.existsById(999L)).thenReturn(false);
        assertFalse(service.deleteDocument(999L));
        verify(store, never()).deleteById(anyLong());
    }

    private Document document(long id) {
        return new Document(id, "invoice.pdf", "application/pdf", "Original",
                LocalDateTime.of(2026, 10, 14, 12, 0));
    }
}
