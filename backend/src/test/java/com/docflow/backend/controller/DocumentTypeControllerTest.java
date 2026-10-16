
package com.docflow.backend.controller;

import com.docflow.backend.dto.DocumentTypeRequest;
import com.docflow.backend.dto.DocumentTypeResponse;
import com.docflow.backend.service.DocumentTypeService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DocumentTypeControllerTest {

    private DocumentTypeService documentTypeService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {

        documentTypeService = mock(DocumentTypeService.class);

        DocumentTypeController controller =
                new DocumentTypeController(documentTypeService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .build();
    }

    @Test
    void createDocumentTypeShouldReturn200() throws Exception {

        // Arrange
        DocumentTypeResponse response =
                new DocumentTypeResponse(
                        1L,
                        "Invoice",
                        "Invoice document"
                );

        when(documentTypeService.create(any(DocumentTypeRequest.class)))
                .thenReturn(response);

        String json = """
                {
                    "name": "Invoice",
                    "description": "Invoice document"
                }
                """;

        // Act & Assert
        mockMvc.perform(post("/document-types")
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Invoice"))
                .andExpect(jsonPath("$.description").value("Invoice document"));

        verify(documentTypeService)
                .create(any(DocumentTypeRequest.class));
    }

    @Test
    void getAllDocumentTypesShouldReturn200() throws Exception {

        // Arrange
        DocumentTypeResponse type1 =
                new DocumentTypeResponse(
                        1L, "Invoice", "Invoice document"
                );

        DocumentTypeResponse type2 =
                new DocumentTypeResponse(
                        2L, "Contract", "Contract document"
                );

        when(documentTypeService.findAll())
                .thenReturn(List.of(type1, type2));

        // Act & Assert
        mockMvc.perform(get("/document-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Invoice"))
                .andExpect(jsonPath("$[1].name").value("Contract"));

        verify(documentTypeService).findAll();
    }
}
