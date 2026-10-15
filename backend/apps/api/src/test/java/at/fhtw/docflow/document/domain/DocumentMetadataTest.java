package at.fhtw.docflow.document.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DocumentMetadataTest {
    @Test
    void requiredFieldsRejectNullBlankAndOversizedValues() {
        for (String invalid : new String[]{null, "", "   ", "x".repeat(256)}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new DocumentMetadata(invalid, "application/pdf", null));
            assertThrows(IllegalArgumentException.class,
                    () -> new DocumentMetadata("invoice.pdf", invalid, null));
        }
    }

    @Test
    void nullableDescriptionStillHasLengthLimit() {
        assertDoesNotThrow(() -> new DocumentMetadata("invoice.pdf", "application/pdf", null));
        assertDoesNotThrow(() -> new DocumentMetadata("x".repeat(255), "x".repeat(255), "x".repeat(255)));
        assertThrows(IllegalArgumentException.class,
                () -> new DocumentMetadata("invoice.pdf", "application/pdf", "x".repeat(256)));
    }

    @Test
    void titleDefaultsToFilenameAndRejectsBlankOrOversizedValues() {
        assertEquals("invoice.pdf", new DocumentMetadata("invoice.pdf", "application/pdf", null, null).title());
        assertEquals("Invoice", new DocumentMetadata("invoice.pdf", "application/pdf", null, "Invoice").title());
        for (String invalid : new String[]{"", "   ", "x".repeat(256)}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new DocumentMetadata("invoice.pdf", "application/pdf", null, invalid));
        }
    }

    @Test
    void documentRequiresOwnerAndValidServerStorageKey() {
        DocumentMetadata metadata = new DocumentMetadata("invoice.pdf", "application/pdf", null);
        for (Long owner : new Long[]{null, 0L, -1L}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new Document(null, "invoice.pdf", "application/pdf", null, null, owner, "Invoice", null));
        }
        for (String key : new String[]{"", "   ", "x".repeat(1025)}) {
            assertThrows(IllegalArgumentException.class, () -> Document.create(7L, metadata, key));
        }
        Document document = Document.create(7L, metadata, "documents/original.pdf");
        Document changed = document.withMetadata(new DocumentMetadata("new.pdf", "application/pdf", null, "New title"));
        assertEquals(document.ownerId(), changed.ownerId());
        assertEquals(document.originalObjectKey(), changed.originalObjectKey());
    }

    @Test
    void invalidMetadataCannotBeIntroducedThroughDocumentConstruction() {
        assertThrows(IllegalArgumentException.class,
                () -> new Document(1L, " ", "application/pdf", null, null, 7L, "Title", null));
    }
}
