package at.fhtw.docflow.document.application;

public class DocumentContentNotFoundException extends RuntimeException {
    public DocumentContentNotFoundException() {
        super("Document content not found");
    }
}
