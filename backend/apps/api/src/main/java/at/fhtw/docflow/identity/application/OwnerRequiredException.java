package at.fhtw.docflow.identity.application;

public class OwnerRequiredException extends RuntimeException {
    public OwnerRequiredException() {
        super("An authenticated owner is required");
    }
}
