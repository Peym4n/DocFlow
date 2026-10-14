package at.fhtw.docflow.document.persistence;

import at.fhtw.docflow.document.persistence.Document;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, Long> {

}
