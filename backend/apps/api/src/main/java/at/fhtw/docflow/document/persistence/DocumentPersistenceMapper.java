package at.fhtw.docflow.document.persistence;

import at.fhtw.docflow.configuration.MappingConfiguration;
import at.fhtw.docflow.document.domain.Document;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = MappingConfiguration.class)
public interface DocumentPersistenceMapper {
    // This immutable business operation is not a field populated by the mapper.
    @Mapping(target = "withMetadata", ignore = true)
    Document toDomain(DocumentEntity entity);

    // Identity and creation time belong to persistence, never to an incoming create command.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    DocumentEntity toNewEntity(Document document);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void updateMetadata(Document document, @MappingTarget DocumentEntity entity);
}
