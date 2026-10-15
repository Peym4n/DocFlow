package at.fhtw.docflow.document.persistence;

import at.fhtw.docflow.document.domain.Document;
import at.fhtw.docflow.identity.persistence.UserEntity;
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
                LocalDateTime.of(2026, 10, 14, 12, 0), 7L, "Invoice", "documents/original.pdf");
        DocumentEntity entity = mapper.toNewEntity(supplied);
        assertNull(entity.getId());
        assertNull(entity.getCreatedAt());
        assertNull(entity.getOwner());
        assertEquals("Invoice", entity.getTitle());
        assertEquals(supplied.originalObjectKey(), entity.getOriginalObjectKey());
    }

    @Test
    void metadataMappingPreservesProtectedFieldsAndClearsNullableDescription() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 14, 12, 0);
        UserEntity owner = mock(UserEntity.class);
        when(owner.getId()).thenReturn(7L);
        DocumentEntity entity = spy(new DocumentEntity("old.pdf", "application/pdf", "Old"));
        entity.setOwner(owner);
        entity.setOriginalObjectKey("documents/original.pdf");
        doReturn(1L).when(entity).getId();
        doReturn(createdAt).when(entity).getCreatedAt();
        mapper.updateMetadata(new Document(999L, "new.pdf", "application/pdf", null,
                createdAt.plusDays(1), 99L, "New title", "documents/forged.pdf"), entity);
        Document mapped = mapper.toDomain(entity);
        assertEquals(1L, mapped.id());
        assertEquals(createdAt, mapped.createdAt());
        assertEquals(7L, mapped.ownerId());
        assertSame(owner, entity.getOwner());
        assertEquals("documents/original.pdf", mapped.originalObjectKey());
        assertEquals("New title", mapped.title());
        assertEquals("new.pdf", mapped.filename());
        assertNull(mapped.description());
    }
}
