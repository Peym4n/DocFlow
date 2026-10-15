package at.fhtw.docflow.document.application;

import at.fhtw.docflow.document.domain.Document;
import at.fhtw.docflow.document.domain.DocumentMetadata;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.UnexpectedRollbackException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentFileServiceTest {
    private final DocumentUseCases documents = mock(DocumentUseCases.class);
    private final DocumentStorage storage = mock(DocumentStorage.class);
    private final PdfValidator validator = mock(PdfValidator.class);
    private final DocumentFileService service = new DocumentFileService(documents, storage, validator);
    private final DocumentMetadata metadata = new DocumentMetadata("invoice.pdf", "application/pdf", null);
    private final byte[] bytes = {1, 2, 3};
    private final Document stored = new Document(1L, "invoice.pdf", "application/pdf", null,
            LocalDateTime.of(2026, 10, 14, 12, 0), 7L, "Invoice", "documents/original.pdf");

    @Test
    void validatesStoresAndCommitsMetadataWithServerGeneratedKey() {
        when(documents.createStoredDocument(eq(7L), eq(metadata), anyString())).thenReturn(stored);
        assertThat(service.upload(7L, metadata, bytes)).isEqualTo(stored);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        var order = inOrder(validator, storage, documents);
        order.verify(validator).validate(bytes);
        order.verify(storage).put(key.capture(), eq(bytes));
        order.verify(documents).createStoredDocument(7L, metadata, key.getValue());
        assertThat(key.getValue()).matches("documents/[a-f0-9-]{36}\\.pdf");
        verify(storage, never()).delete(anyString());
    }

    @Test
    void compensatesEvenWhenTransactionCommitFails() {
        when(documents.createStoredDocument(eq(7L), eq(metadata), anyString()))
                .thenThrow(new UnexpectedRollbackException("commit failure"));
        assertThatThrownBy(() -> service.upload(7L, metadata, bytes)).isInstanceOf(UnexpectedRollbackException.class);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        var order = inOrder(storage, documents);
        order.verify(storage).put(key.capture(), eq(bytes));
        order.verify(documents).createStoredDocument(7L, metadata, key.getValue());
        order.verify(storage).delete(key.getValue());
    }

    @Test
    void cleanupFailureDoesNotHideOriginalFailure() {
        when(documents.createStoredDocument(eq(7L), eq(metadata), anyString())).thenThrow(new IllegalStateException("database failure"));
        doThrow(new StorageUnavailableException()).when(storage).delete(anyString());
        assertThatThrownBy(() -> service.upload(7L, metadata, bytes))
                .isInstanceOf(IllegalStateException.class).hasMessage("database failure");
    }

    @Test
    void failedStorageWriteDoesNotCreateMetadataAndAttemptsCleanup() {
        doThrow(new StorageUnavailableException()).when(storage).put(anyString(), any());
        assertThatThrownBy(() -> service.upload(7L, metadata, bytes)).isInstanceOf(StorageUnavailableException.class);
        verifyNoInteractions(documents);
        verify(storage).delete(anyString());
    }

    @Test
    void invalidPdfDoesNotReachStorageOrDatabase() {
        doThrow(new InvalidPdfException("Invalid PDF")).when(validator).validate(bytes);
        assertThatThrownBy(() -> service.upload(7L, metadata, bytes)).isInstanceOf(InvalidPdfException.class);
        verifyNoInteractions(storage, documents);
        assertThatThrownBy(() -> service.upload(7L, new DocumentMetadata("file.txt", "text/plain", null), bytes))
                .isInstanceOf(InvalidPdfException.class);
    }

    @Test
    void contentRequiresOwnedDocumentAndStoredKey() {
        when(documents.getDocumentById(7L, 1L)).thenReturn(Optional.of(stored));
        when(storage.get(stored.originalObjectKey())).thenReturn(bytes);
        DocumentContent result = service.getContent(7L, 1L);
        assertThat(result.filename()).isEqualTo(stored.filename());
        assertThat(result.bytes()).isEqualTo(bytes);
        verify(documents, times(1)).getDocumentById(7L, 1L);
        when(documents.getDocumentById(8L, 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getContent(8L, 1L)).isInstanceOf(DocumentNotFoundException.class);
        when(documents.getDocumentById(7L, 1L)).thenReturn(Optional.of(Document.create(7L, metadata)));
        assertThatThrownBy(() -> service.getContent(7L, 1L)).isInstanceOf(DocumentContentNotFoundException.class);
    }

    @Test
    void deletionCommitsBeforeExternalCleanupAndKeepsSuccessIfCleanupFails() {
        when(documents.getDocumentById(7L, 1L)).thenReturn(Optional.of(stored));
        when(documents.deleteDocument(7L, 1L)).thenReturn(true);
        doThrow(new StorageUnavailableException()).when(storage).delete(stored.originalObjectKey());
        assertThat(service.deleteDocument(7L, 1L)).isTrue();
        var order = inOrder(documents, storage);
        order.verify(documents).getDocumentById(7L, 1L);
        order.verify(documents).deleteDocument(7L, 1L);
        order.verify(storage).delete(stored.originalObjectKey());
    }

    @Test
    void missingForeignOrRacingDeletionDoesNotDeleteObjects() {
        assertThat(service.deleteDocument(8L, 1L)).isFalse();
        when(documents.getDocumentById(7L, 1L)).thenReturn(Optional.of(stored));
        when(documents.deleteDocument(7L, 1L)).thenReturn(false);
        assertThat(service.deleteDocument(7L, 1L)).isFalse();
        verifyNoInteractions(storage);
    }

    @Test
    void failedDatabaseDeleteDoesNotRemoveObject() {
        when(documents.getDocumentById(7L, 1L)).thenReturn(Optional.of(stored));
        when(documents.deleteDocument(7L, 1L)).thenThrow(new IllegalStateException("database failure"));
        assertThatThrownBy(() -> service.deleteDocument(7L, 1L)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(storage);
    }

    @Test
    void metadataOnlyDeletionDoesNotUseStorage() {
        when(documents.getDocumentById(7L, 1L)).thenReturn(Optional.of(Document.create(7L, metadata)));
        when(documents.deleteDocument(7L, 1L)).thenReturn(true);
        assertThat(service.deleteDocument(7L, 1L)).isTrue();
        verifyNoInteractions(storage);
    }
}
