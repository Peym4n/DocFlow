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
    void invalidMetadataCannotBeIntroducedThroughDocumentConstruction() {
        assertThrows(IllegalArgumentException.class,
                () -> new Document(1L, " ", "application/pdf", null, null));
    }
}
