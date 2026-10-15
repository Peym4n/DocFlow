package at.fhtw.docflow.document.domain;

public record DocumentMetadata(String filename, String contentType, String description, String title) {
    public DocumentMetadata(String filename, String contentType, String description) {
        this(filename, contentType, description, filename);
    }

    public DocumentMetadata {
        title = title == null ? filename : title;
        requireText(title, "title");
        requireText(filename, "filename");
        requireText(contentType, "contentType");
        if (description != null && description.length() > 255) {
            throw new IllegalArgumentException("description must contain at most 255 characters");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 255) {
            throw new IllegalArgumentException(field + " must be nonblank and contain at most 255 characters");
        }
    }
}
