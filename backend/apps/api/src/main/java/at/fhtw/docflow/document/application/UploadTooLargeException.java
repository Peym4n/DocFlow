package at.fhtw.docflow.document.application;

public class UploadTooLargeException extends RuntimeException {
    public UploadTooLargeException() {
        super("PDF exceeds the configured upload limit");
    }
}
