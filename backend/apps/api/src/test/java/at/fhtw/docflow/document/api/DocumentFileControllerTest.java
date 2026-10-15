package at.fhtw.docflow.document.api;

import at.fhtw.docflow.api.ApiExceptionHandler;
import at.fhtw.docflow.configuration.UploadProperties;
import at.fhtw.docflow.document.application.DocumentContent;
import at.fhtw.docflow.document.application.DocumentFileUseCases;
import at.fhtw.docflow.document.application.DocumentUseCases;
import at.fhtw.docflow.document.application.InvalidPdfException;
import at.fhtw.docflow.document.application.UploadTooLargeException;
import at.fhtw.docflow.document.domain.Document;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DocumentFileControllerTest {
    private final DocumentFileUseCases files = mock(DocumentFileUseCases.class);
    private final DocumentUseCases documents = mock(DocumentUseCases.class);
    private final DocumentFileController controller = new DocumentFileController(files, documents, () -> 7L, new UploadProperties(1024, 10));
    private final MockMvc mvc;

    DocumentFileControllerTest() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).setValidator(validator).build();
    }

    @Test
    void acceptsPlainFormFieldsAndStripsClientPaths() throws Exception {
        when(files.upload(eq(7L), any(), any())).thenReturn(new Document(1L, "invoice.pdf", "application/pdf", null,
                LocalDateTime.of(2026, 10, 14, 12, 0), 7L, "Invoice", "documents/original.pdf"));
        mvc.perform(multipart("/api/v1/documents")
                        .file(new MockMultipartFile("file", "C:\\uploads\\invoice.pdf", "application/pdf", new byte[]{1}))
                        .param("title", "Invoice").param("description", "Example"))
                .andExpect(status().isAccepted()).andExpect(header().string("Location", "/api/v1/documents/1/processing"))
                .andExpect(jsonPath("$.status").value("DEFERRED"));
        verify(files).upload(eq(7L), argThat(metadata -> metadata.filename().equals("invoice.pdf")
                && metadata.title().equals("Invoice") && metadata.description().equals("Example")), any());
    }

    @Test
    void validatesRequiredFileAndFormFieldLengths() throws Exception {
        mvc.perform(multipart("/api/v1/documents")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.file").exists());
        mvc.perform(multipart("/api/v1/documents").file(new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[]{1}))
                        .param("title", "x".repeat(256)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.title").exists());
        verifyNoInteractions(files);
    }

    @Test
    void rejectsControlCharactersAndDeclaredOversize() throws Exception {
        mvc.perform(multipart("/api/v1/documents").file(new MockMultipartFile("file", "bad\r\nname.pdf", "application/pdf", new byte[]{1})))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/v1/documents").file(new MockMultipartFile("file", "large.pdf", "application/pdf", new byte[1025])))
                .andExpect(status().is(413));
        verifyNoInteractions(files);
    }

    @Test
    void boundsActualStreamEvenWhenDeclaredSizeIsWrong() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("invoice.pdf");
        when(file.getContentType()).thenReturn("application/pdf");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[1025]));
        assertThatThrownBy(() -> controller.upload(new DocumentRequest(file, null, null))).isInstanceOf(UploadTooLargeException.class);
        verifyNoInteractions(files);
    }

    @Test
    void handlesMissingFilenameAndUnreadableUpload() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        assertThatThrownBy(() -> controller.upload(new DocumentRequest(file, null, null))).isInstanceOf(InvalidPdfException.class);
        when(file.getOriginalFilename()).thenReturn("invoice.pdf");
        when(file.getContentType()).thenReturn("application/pdf");
        when(file.getInputStream()).thenThrow(new IOException("sensitive internal path"));
        assertThatThrownBy(() -> controller.upload(new DocumentRequest(file, null, null))).isInstanceOf(InvalidPdfException.class)
                .hasMessage("Unable to read the uploaded PDF");
        verifyNoInteractions(files);
    }

    @Test
    void downloadUsesOneApplicationCallForFilenameAndBytes() throws Exception {
        when(files.getContent(7L, 1L)).thenReturn(new DocumentContent("invoice.pdf", new byte[]{1, 2, 3}));
        mvc.perform(get("/api/v1/documents/1/content"))
                .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
                .andExpect(content().bytes(new byte[]{1, 2, 3})).andExpect(header().string("Cache-Control", "no-store"));
        verify(files).getContent(7L, 1L);
        verifyNoInteractions(documents);
    }
}
