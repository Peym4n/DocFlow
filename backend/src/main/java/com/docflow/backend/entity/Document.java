package com.docflow.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import org.hibernate.annotations.CreationTimestamp;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "documents")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String filename;
    private String contentType;
    private String description;

    @CreationTimestamp
    private LocalDateTime createdAt;

    protected Document() {

    }

    public Document(String filename, String contentType, String description) {
        this.filename = filename;
        this.contentType = contentType;
        this.description = description;
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

    public void updateMetaDate(String filename, String contentType, String description) {
        this.filename = filename;
        this.contentType = contentType;
        this.description = description;
    }


    @ManyToMany
    @JoinTable(
            name = "document_document_types",
            joinColumns = @JoinColumn(name = "document_id"),
            inverseJoinColumns = @JoinColumn(name = "document_type_id")
    )
    private Set<DocumentType> documentTypes = new HashSet<>();

    public Set<DocumentType> getDocumentTypes() {
        return documentTypes;
    }






}
