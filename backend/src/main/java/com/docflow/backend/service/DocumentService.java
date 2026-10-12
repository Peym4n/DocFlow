package com.docflow.backend.service;

import com.docflow.backend.entity.Document;
import com.docflow.backend.repository.DocumentRepository;
import org.springframework.stereotype.Service;

import javax.print.Doc;
import java.util.List;
import java.util.Optional;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;

    public DocumentService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    public Document createDocument(Document document) {
        return documentRepository.save(document);
    }

    public List<Document> getAllDocuments() {
        return documentRepository.findAll();
    }

    public Optional<Document> getDocumentById(Long id) {
        return documentRepository.findById(id);
    }

    public Optional<Document> updateDocument(Long id, Document updateDocument) {
        return documentRepository.findById(id)
                .map(document -> {
                    document.updateMetaDate(
                            updateDocument.getFilename(),
                            updateDocument.getContentType(),
                            updateDocument.getDescription()
                    );
                    return documentRepository.save(document);
                });
    }

    public boolean deleteDocument(Long id) {
        if (!documentRepository.existsById(id)) {
            return false;
        }

        documentRepository.deleteById(id);
        return true;
    }
}

