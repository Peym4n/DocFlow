package at.fhtw.docflow.document.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<DocumentEntity, Long> {
    List<DocumentEntity> findAllByOwnerId(long ownerId);
    Optional<DocumentEntity> findByIdAndOwnerId(Long id, long ownerId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from DocumentEntity d where d.id = :id and d.owner.id = :ownerId")
    int deleteOwnedDocument(@Param("id") Long id, @Param("ownerId") long ownerId);
}
