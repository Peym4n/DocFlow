package at.fhtw.docflow.document.api;

import at.fhtw.docflow.configuration.MappingConfiguration;
import at.fhtw.docflow.document.domain.Document;
import at.fhtw.docflow.document.domain.DocumentMetadata;
import org.mapstruct.Mapper;

@Mapper(config = MappingConfiguration.class)
public interface DocumentMapper {
    DocumentMetadata toMetadata(DocumentRequest request);
    DocumentMetadata toMetadata(DocumentMetadataRequest request);
    DocumentResponse toResponse(Document document);
}
