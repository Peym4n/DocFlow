package com.docflow.backend.service;

import com.docflow.backend.entity.Document;
import com.docflow.backend.entity.DocumentType;
import com.docflow.backend.repository.DocumentRepository;
import com.docflow.backend.repository.DocumentTypeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.print.Doc;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentTypeRepository documentTypeRepository;

    @InjectMocks
    private DocumentService documentService;

    @Test
    void createDocumentShouldSaveDocument() {

        // Arrange
        Document document = new Document(
                "rechnung.pdf",
                "application/pdf",
                "Stromrechnung"
        );

        when(documentRepository.save(document))
                .thenReturn(document);

        // Act
        Document result = documentService.createDocument(document);

        // Assert
        assertSame(document, result);
        verify(documentRepository).save(document);
    }

    @Test
    void getAllDocumentsShouldReturnDocuments() {
        // Arrange
        Document doc1 = new Document(
                "rechnung.pdf", "application/pdf", "Rechung"
        );

        Document doc2 = new Document(
                "vertrag.pdf", "application/pdf", "Vertrag"
        );

        List<Document> documents = List.of(doc1, doc2);

        when(documentRepository.findAll()).thenReturn(documents);

        // Act
        List<Document> result = documentService.getAllDocuments();

        // Assert
        assertEquals(2, result.size());
        assertEquals(documents, result);
        verify(documentRepository).findAll();
    }

    @Test
    void getDocumentByIdShouldReturnDocument() {

        // Arrange
        Long id = 1L;
        Document document = new Document(
                "rechung.pdf", "application/pdf", "Rechnung"
        );

        when(documentRepository.findById(id))
                .thenReturn(Optional.of(document));

        // Act
        Optional<Document> result = documentService.getDocumentById(id);

        // Assert
        assertTrue(result.isPresent());
        assertSame(document, result.get());
        verify(documentRepository).findById(id);
    }


    @Test
    void getDocumentByIdShouldReturnEmptyWhenNotFound() {

        // Arrange
        Long id = 999L;

        when(documentRepository.findById(id))
                .thenReturn(Optional.empty());

        // Act
        Optional<Document> result = documentService.getDocumentById(id);

        // Assert
        assertTrue(result.isEmpty());
        verify(documentRepository).findById(id);
    }


    @Test
    void updateDocumentShouldUpdateMetadata() {

        // Arrange
        Long id = 1L;

        Document existing = new Document(
                "alt.pdf", "application/pdf", "Alte Beschreibung"
        );

        Document updated = new Document(
                "neu.pdf", "application/pdf", "Neue Beschreibung"
        );

        when(documentRepository.findById(id))
                .thenReturn(Optional.of(existing));

        when(documentRepository.save(existing))
                .thenReturn(existing);

        // Act
        Optional<Document> result =
                documentService.updateDocument(id, updated);

        // Assert
        assertTrue(result.isPresent());
        assertEquals("neu.pdf", result.get().getFilename());
        assertEquals("application/pdf", result.get().getContentType());
        assertEquals("Neue Beschreibung", result.get().getDescription());

        verify(documentRepository).findById(id);
        verify(documentRepository).save(existing);
    }


    @Test
    void updateDocumentShouldReturnEmptyWhenNotFound() {

        // Arrange
        Long id = 999L;

        Document updated = new Document(
                "neu.pdf", "application/pdf", "Beschreibung"
        );

        when(documentRepository.findById(id))
                .thenReturn(Optional.empty());

        // Act
        Optional<Document> result =
                documentService.updateDocument(id, updated);

        // Assert
        assertTrue(result.isEmpty());

        verify(documentRepository).findById(id);
        verify(documentRepository, never()).save(any(Document.class));
    }

    @Test
    void deleteDocumentShouldDeleteExistingDocument() {

        // Arrange
        Long id = 1L;

        when(documentRepository.existsById(id)).thenReturn(true);

        // Act
        boolean result = documentService.deleteDocument(id);

        // Assert
        assertTrue(result);
        verify(documentRepository).existsById(id);
        verify(documentRepository).deleteById(id);
    }


    @Test
    void deleteDocumentShouldReturnFalseWhenNotFound() {

        // Arrange
        Long id = 999L;

        when(documentRepository.existsById(id)).thenReturn(false);

        // Act
        boolean result = documentService.deleteDocument(id);

        // Assert
        assertFalse(result);

        verify(documentRepository).existsById(id);
        verify(documentRepository, never()).deleteById(anyLong());
    }


    @Test
    void addDocumentTypeShouldAssignTypeToDocument() {

        // Arrange
        Long documentId = 1L;
        Long typeId = 2L;

        Document document = new Document(
                "rechnung.pdf",
                "application/pdf",
                "Stromrechnung"
        );

        DocumentType documentType = new DocumentType(
                "Invoice",
                "Invoice document"
        );

        when(documentRepository.findById(documentId))
                .thenReturn(Optional.of(document));

        when(documentTypeRepository.findById(typeId))
                .thenReturn(Optional.of(documentType));

        // Act
        documentService.addDocumentType(documentId, typeId);

        // Assert
        assertTrue(document.getDocumentTypes().contains(documentType));

        verify(documentRepository).save(document);
    }


    @Test
    void addDocumentTypeShouldThrowWhenDocumentNotFound() {

        // Arrange
        Long documentId = 999L;
        Long typeId = 2L;

        when(documentRepository.findById(documentId))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(
                NoSuchElementException.class,
                () -> documentService.addDocumentType(documentId, typeId)
        );

        verify(documentRepository, never())
                .save(any(Document.class));
    }



    @Test
    void addDocumentTypeShouldThrowWhenTypeNotFound() {

        // Arrange
        Long documentId = 1L;
        Long typeId = 999L;

        Document document = new Document(
                "rechnung.pdf",
                "application/pdf",
                "Stromrechnung"
        );

        when(documentRepository.findById(documentId))
                .thenReturn(Optional.of(document));

        when(documentTypeRepository.findById(typeId))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(
                NoSuchElementException.class,
                () -> documentService.addDocumentType(documentId, typeId)
        );

        assertTrue(document.getDocumentTypes().isEmpty());

        verify(documentRepository, never())
                .save(any(Document.class));
    }



}
