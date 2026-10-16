package com.docflow.backend.repository;

import com.docflow.backend.entity.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentTypeRepository extends JpaRepository<DocumentType, Long> {
}
