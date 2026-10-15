package at.fhtw.docflow.document.application;

public interface DocumentStorage {
    void put(String key, byte[] content);
    byte[] get(String key);
    void delete(String key);
    boolean available();
}
