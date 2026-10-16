
package com.docflow.backend.service;

import com.docflow.backend.dto.DocumentTypeRequest;
import com.docflow.backend.dto.DocumentTypeResponse;
import com.docflow.backend.entity.DocumentType;
import com.docflow.backend.mapper.DocumentTypeMapper;
import com.docflow.backend.repository.DocumentTypeRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentTypeServiceTest {

    @Mock
    private DocumentTypeRepository documentTypeRepository;

    @Mock
    private DocumentTypeMapper documentTypeMapper;

    @InjectMocks
    private DocumentTypeService documentTypeService;

    @Test
    void createDocumentTypeShouldSaveDocumentType() {

        // Arrange
        DocumentTypeRequest request =
                new DocumentTypeRequest("Invoice", "Invoice document");

        DocumentType documentType =
                new DocumentType("Invoice", "Invoice document");

        DocumentTypeResponse response =
                new DocumentTypeResponse(1L, "Invoice", "Invoice document");

        when(documentTypeMapper.toEntity(request))
                .thenReturn(documentType);

        when(documentTypeRepository.save(documentType))
                .thenReturn(documentType);

        when(documentTypeMapper.toResponse(documentType))
                .thenReturn(response);

        // Act
        DocumentTypeResponse result =
                documentTypeService.create(request);

        // Assert
        assertEquals("Invoice", result.name());
        assertEquals("Invoice document", result.description());

        verify(documentTypeRepository).save(documentType);
    }

    @Test
    void getAllDocumentTypesShouldReturnDocumentTypes() {

        // Arrange
        DocumentType type1 =
                new DocumentType("Invoice", "Invoice document");

        DocumentType type2 =
                new DocumentType("Contract", "Contract document");

        DocumentTypeResponse response1 =
                new DocumentTypeResponse(1L, "Invoice", "Invoice document");

        DocumentTypeResponse response2 =
                new DocumentTypeResponse(2L, "Contract", "Contract document");

        when(documentTypeRepository.findAll())
                .thenReturn(List.of(type1, type2));

        when(documentTypeMapper.toResponse(type1))
                .thenReturn(response1);

        when(documentTypeMapper.toResponse(type2))
                .thenReturn(response2);

        // Act
        List<DocumentTypeResponse> result =
                documentTypeService.findAll();

        // Assert
        assertEquals(2, result.size());
        assertEquals("Invoice", result.get(0).name());
        assertEquals("Contract", result.get(1).name());

        verify(documentTypeRepository).findAll();
    }
}
