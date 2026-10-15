package at.fhtw.docflow.configuration;

import at.fhtw.docflow.document.application.DocumentStorage;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("storage")
public class StorageHealthIndicator implements HealthIndicator {
    private final DocumentStorage storage;

    public StorageHealthIndicator(DocumentStorage storage) {
        this.storage = storage;
    }

    @Override
    public Health health() {
        return (storage.available() ? Health.up() : Health.down()).build();
    }
}
