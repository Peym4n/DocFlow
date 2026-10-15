package at.fhtw.docflow.document.integration;

import at.fhtw.docflow.PdfFixtures;
import at.fhtw.docflow.configuration.UploadProperties;
import at.fhtw.docflow.document.application.InvalidPdfException;
import at.fhtw.docflow.document.application.UploadTooLargeException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class PdfBoxValidatorTest {
    private final PdfBoxValidator validator = new PdfBoxValidator(new UploadProperties(10485760, 2));

    @Test
    void acceptsAReadablePdfWithinLimits() throws Exception {
        assertThatCode(() -> validator.validate(PdfFixtures.pdf(2))).doesNotThrowAnyException();
    }

    @Test
    void rejectsEmptyNonPdfAndMalformedPdf() {
        for (byte[] invalid : new byte[][]{new byte[0], "text pretending to be pdf".getBytes(StandardCharsets.UTF_8),
                "%PDF-1.7\ninvalid".getBytes(StandardCharsets.UTF_8)}) {
            assertThatThrownBy(() -> validator.validate(invalid)).isInstanceOf(InvalidPdfException.class);
        }
    }

    @Test
    void rejectsTooManyPages() throws Exception {
        assertThatThrownBy(() -> validator.validate(PdfFixtures.pdf(3))).isInstanceOf(InvalidPdfException.class);
    }

    @Test
    void rejectsOversizedBytes() {
        assertThatThrownBy(() -> validator.validate(new byte[10485761])).isInstanceOf(UploadTooLargeException.class);
    }

    @Test
    void rejectsEncryptedPdfEvenWithEmptyUserPassword() throws Exception {
        try (PDDocument pdf = Loader.loadPDF(PdfFixtures.pdf(1));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            pdf.protect(new StandardProtectionPolicy("owner-password", "", new AccessPermission()));
            pdf.save(output);
            assertThatThrownBy(() -> validator.validate(output.toByteArray())).isInstanceOf(InvalidPdfException.class);
        }
    }

    @Test
    void rejectsPdfRequiringPassword() throws Exception {
        try (PDDocument pdf = Loader.loadPDF(PdfFixtures.pdf(1));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            pdf.protect(new StandardProtectionPolicy("owner-password", "user-password", new AccessPermission()));
            pdf.save(output);
            assertThatThrownBy(() -> validator.validate(output.toByteArray())).isInstanceOf(InvalidPdfException.class);
        }
    }
}
