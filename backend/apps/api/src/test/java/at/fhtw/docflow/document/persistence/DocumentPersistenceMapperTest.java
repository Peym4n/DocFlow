package at.fhtw.docflow.document.persistence;

import at.fhtw.docflow.document.domain.Document;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentPersistenceMapperTest {
    private final DocumentPersistenceMapper mapper = Mappers.getMapper(DocumentPersistenceMapper.class);

    @Test
    void creationDoesNotCopySuppliedIdentityOrCreationTime() {
        Document supplied = new Document(42L, "invoice.pdf", "application/pdf", null,
                LocalDateTime.of(2026, 10, 14, 12, 0));
        DocumentEntity entity = mapper.toNewEntity(supplied);
        assertNull(entity.getId());
        assertNull(entity.getCreatedAt());
    }

    @Test
    void metadataMappingPreservesExistingIdentityAndClearsNullableDescription() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 14, 12, 0);
        DocumentEntity entity = spy(new DocumentEntity("old.pdf", "application/pdf", "Old"));
        doReturn(1L).when(entity).getId();
        doReturn(createdAt).when(entity).getCreatedAt();
        mapper.updateMetadata(new Document(999L, "new.pdf", "application/pdf", null,
                createdAt.plusDays(1)), entity);
        Document mapped = mapper.toDomain(entity);
        assertEquals(1L, mapped.id());
        assertEquals(createdAt, mapped.createdAt());
        assertEquals("new.pdf", mapped.filename());
        assertNull(mapped.description());
    }
}
