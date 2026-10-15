package at.fhtw.docflow.document.application;

public class StorageUnavailableException extends RuntimeException {
    public StorageUnavailableException() {
        super("Document storage temporarily unavailable");
    }

    public StorageUnavailableException(Throwable cause) {
        super("Document storage temporarily unavailable", cause);
    }
}
