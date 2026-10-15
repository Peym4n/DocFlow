package at.fhtw.docflow.document.application;

public class InvalidPdfException extends RuntimeException {
    public InvalidPdfException(String message) {
        super(message);
    }
}
