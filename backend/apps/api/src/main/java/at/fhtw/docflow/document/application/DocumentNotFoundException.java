package at.fhtw.docflow.document.application;

public class DocumentNotFoundException extends RuntimeException {
    public DocumentNotFoundException(Long id) {
        super("Document " + id + " was not found");
    }
}
