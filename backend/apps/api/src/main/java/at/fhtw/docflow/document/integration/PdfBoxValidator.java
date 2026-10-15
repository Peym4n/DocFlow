package at.fhtw.docflow.document.integration;

import at.fhtw.docflow.configuration.UploadProperties;
import at.fhtw.docflow.document.application.InvalidPdfException;
import at.fhtw.docflow.document.application.PdfValidator;
import at.fhtw.docflow.document.application.UploadTooLargeException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.Loader;
import org.springframework.stereotype.Component;

@Component
public class PdfBoxValidator implements PdfValidator {
    private final UploadProperties limits;

    public PdfBoxValidator(UploadProperties limits) {
        this.limits = limits;
    }

    @Override
    public void validate(byte[] content) {
        if (content.length > limits.maxBytes()) {
            throw new UploadTooLargeException();
        }
        if (content.length < 5 || !new String(content, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
            throw new InvalidPdfException("A valid PDF file is required");
        }
        try (var pdf = Loader.loadPDF(content)) {
            if (pdf.isEncrypted()) {
                throw new InvalidPdfException("Encrypted PDFs are not supported");
            }
            if (pdf.getNumberOfPages() < 1 || pdf.getNumberOfPages() > limits.maxPages()) {
                throw new InvalidPdfException("PDF page count is outside the configured limits");
            }
        } catch (IOException exception) {
            throw new InvalidPdfException("A readable, unencrypted PDF file is required");
        }
    }
}
