package at.fhtw.docflow.document.persistence;

import at.fhtw.docflow.identity.persistence.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "document")
public class DocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, updatable = false)
    private UserEntity owner;

    @Column(nullable = false)
    private String title;

    @Column(length = 1024, unique = true, updatable = false)
    private String originalObjectKey;

    private String filename;
    private String contentType;
    private String description;

    @CreationTimestamp
    private LocalDateTime createdAt;

    protected DocumentEntity() {

    }

    public DocumentEntity(String filename, String contentType, String description) {
        this.filename = filename;
        this.contentType = contentType;
        this.description = description;
    }

    public UserEntity getOwner() {
        return owner;
    }

    public void setOwner(UserEntity owner) {
        this.owner = owner;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getOriginalObjectKey() {
        return originalObjectKey;
    }

    public void setOriginalObjectKey(String originalObjectKey) {
        this.originalObjectKey = originalObjectKey;
    }

    public Long getId() {
        return id;
    }

    public String getFilename() {
        return filename;
    }

    public String getContentType() {
        return contentType;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
