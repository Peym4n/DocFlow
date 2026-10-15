package at.fhtw.docflow.document.api;

import at.fhtw.docflow.api.ApiExceptionHandler;
import at.fhtw.docflow.document.domain.Document;
import at.fhtw.docflow.document.domain.DocumentMetadata;
import at.fhtw.docflow.document.application.DocumentUseCases;
import org.mapstruct.factory.Mappers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.transaction.CannotCreateTransactionException;
import java.time.LocalDateTime;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DocumentControllerTest {

    private DocumentUseCases documentService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        documentService = mock(DocumentUseCases.class);

        DocumentController controller =
                new DocumentController(documentService, Mappers.getMapper(DocumentMapper.class), () -> 7L);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void getDocumentByIdShouldReturn404WhenNotFound() throws Exception {

        // Arrange
        when(documentService.getDocumentById(7L, 999L))
                .thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(get("/api/v1/documents/999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"));

        verify(documentService).getDocumentById(7L, 999L);
    }


    @Test
    void getDocumentByIdShouldReturn200WhenFound() throws Exception {

        // Arrange
        Document document = Document.create(7L, new DocumentMetadata(
                "rechnung.pdf",
                "application/pdf",
                "Stromrechnung"
        ));

        when(documentService.getDocumentById(7L, 1L))
                .thenReturn(Optional.of(document));

        // Act & Assert
        mockMvc.perform(get("/api/v1/documents/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").value("rechnung.pdf"))
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.description").value("Stromrechnung"));

        verify(documentService).getDocumentById(7L, 1L);
    }

    @Test
    void getAllDocumentsShouldReturn200() throws Exception {

        // Arrange
        Document doc1 = Document.create(7L, new DocumentMetadata(
                "rechnung.pdf", "application/pdf", "Rechnung"
        ));

        Document doc2 = Document.create(7L, new DocumentMetadata(
                "vertrag.pdf", "application/pdf", "Vertrag"
        ));

        when(documentService.getAllDocuments(7L))
                .thenReturn(List.of(doc1, doc2));

        // Act & Assert
        mockMvc.perform(get("/api/v1/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].filename").value("rechnung.pdf"))
                .andExpect(jsonPath("$[1].filename").value("vertrag.pdf"));

        verify(documentService).getAllDocuments(7L);
    }

    @Test
    void createDocumentShouldReturn201() throws Exception {

        // Arrange
        Document document = new Document(1L, "rechnung.pdf", "application/pdf", "Rechnung",
                LocalDateTime.of(2026, 10, 14, 12, 0), 7L, "Invoice", null);

        when(documentService.createDocument(eq(7L), any(DocumentMetadata.class)))
                .thenReturn(document);

        String json = """
            {
                "filename": "rechnung.pdf",
                "contentType": "application/pdf",
                "description": "Rechnung"
            }
            """;

        // Act & Assert
        mockMvc.perform(post("/api/v1/documents")
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/documents/1"))
                .andExpect(jsonPath("$.filename").value("rechnung.pdf"))
                .andExpect(jsonPath("$.description").value("Rechnung"));

        verify(documentService).createDocument(eq(7L), any(DocumentMetadata.class));
    }

    @Test
    void updateDocumentShouldReturn200() throws Exception {

        // Arrange
        Long id = 1L;

        Document updated = Document.create(7L, new DocumentMetadata(
                "neu.pdf", "application/pdf", "Neue Beschreibung"
        ));

        when(documentService.updateDocument(
                eq(7L), eq(id), any(DocumentMetadata.class)))
                .thenReturn(Optional.of(updated));

        String json = """
            {
                "filename": "neu.pdf",
                "contentType": "application/pdf",
                "description": "Neue Beschreibung"
            }
            """;

        // Act & Assert
        mockMvc.perform(put("/api/v1/documents/{id}/metadata", id)
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").value("neu.pdf"))
                .andExpect(jsonPath("$.description").value("Neue Beschreibung"));

        verify(documentService).updateDocument(
                eq(7L), eq(id), any(DocumentMetadata.class));
    }

    @Test
    void updateDocumentShouldReturn404WhenNotFound() throws Exception {

        // Arrange
        Long id = 999L;

        when(documentService.updateDocument(
                eq(7L), eq(id), any(DocumentMetadata.class)))
                .thenReturn(Optional.empty());

        String json = """
            {
                "filename": "neu.pdf",
                "contentType": "application/pdf",
                "description": "Neue Beschreibung"
            }
            """;

        // Act & Assert
        mockMvc.perform(put("/api/v1/documents/{id}/metadata", id)
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"));

        verify(documentService).updateDocument(
                eq(7L), eq(id), any(DocumentMetadata.class));
    }

    @Test
    void deleteDocumentShouldReturn204() throws Exception {

        // Arrange
        Long id = 1L;

        when(documentService.deleteDocument(7L, id))
                .thenReturn(true);

        // Act & Assert
        mockMvc.perform(delete("/api/v1/documents/{id}", id))
                .andExpect(status().isNoContent());

        verify(documentService).deleteDocument(7L, id);
    }


    @Test
    void deleteDocumentShouldReturn404WhenNotFound() throws Exception {

        // Arrange
        Long id = 999L;

        when(documentService.deleteDocument(7L, id))
                .thenReturn(false);

        // Act & Assert
        mockMvc.perform(delete("/api/v1/documents/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"));

        verify(documentService).deleteDocument(7L, id);
    }


    @Test
    void createDocumentShouldReturn400WhenFilenameIsBlank() throws Exception {

        // Arrange
        String json = """
            {
                "filename": "",
                "contentType": "application/pdf",
                "description": "Stromrechnung"
            }
            """;

        // Act & Assert
        mockMvc.perform(post("/api/v1/documents")
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.filename").exists());

        verify(documentService, never())
                .createDocument(eq(7L), any(DocumentMetadata.class));
    }

    @Test
    void updateDocumentShouldRejectMissingRequiredMetadata() throws Exception {
        mockMvc.perform(put("/api/v1/documents/1/metadata")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.filename").exists())
                .andExpect(jsonPath("$.errors.contentType").exists());
        verifyNoInteractions(documentService);
    }

    @Test
    void updateDocumentShouldClearDescriptionAndPreserveResponseIdentity() throws Exception {
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 14, 12, 0);
        DocumentMetadata replacement = new DocumentMetadata("new.pdf", "application/pdf", null);
        when(documentService.updateDocument(7L, 1L, replacement)).thenReturn(Optional.of(
                new Document(1L, replacement.filename(), replacement.contentType(), null, createdAt, 7L, replacement.title(), "documents/original.pdf")));
        mockMvc.perform(put("/api/v1/documents/1/metadata")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filename\":\"new.pdf\",\"contentType\":\"application/pdf\",\"description\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.createdAt").value("2026-10-14T12:00:00"))
                .andExpect(jsonPath("$.description").doesNotExist());
        verify(documentService).updateDocument(7L, 1L, replacement);
    }

    @Test
    void updateDocumentShouldRejectOversizedMetadata() throws Exception {
        mockMvc.perform(put("/api/v1/documents/1/metadata")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filename\":\"" + "x".repeat(256) + "\",\"contentType\":\"application/pdf\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.filename").exists());
        verifyNoInteractions(documentService);
    }

    @Test
    void malformedJsonShouldReturnProblemWithoutParserDetails() throws Exception {
        mockMvc.perform(post("/api/v1/documents")
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(documentService);
    }

    @Test
    void invalidIdShouldReturnBadRequestProblem() throws Exception {
        mockMvc.perform(get("/api/v1/documents/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(documentService);
    }

    @Test
    void databaseConflictShouldNotExposeSql() throws Exception {
        when(documentService.createDocument(eq(7L), any(DocumentMetadata.class)))
                .thenThrow(new DataIntegrityViolationException("sensitive SQL and file paths"));
        mockMvc.perform(post("/api/v1/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filename\":\"invoice.pdf\",\"contentType\":\"application/pdf\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("The operation conflicts with stored data"));
    }

    @Test
    void databaseFailureShouldReturnUnavailableProblem() throws Exception {
        when(documentService.getAllDocuments(7L))
                .thenThrow(new DataAccessResourceFailureException("sensitive connection details"));
        mockMvc.perform(get("/api/v1/documents"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Database temporarily unavailable"));
    }

    @Test
    void oldMetadataUpdatePathShouldNotAcceptPut() throws Exception {
        mockMvc.perform(put("/api/v1/documents/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(documentService);
    }

    @Test
    void databaseTransactionStartFailureShouldReturnUnavailableProblem() throws Exception {
        when(documentService.getAllDocuments(7L))
                .thenThrow(new CannotCreateTransactionException("sensitive database connection details"));
        mockMvc.perform(get("/api/v1/documents"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Database temporarily unavailable"));
    }
}
