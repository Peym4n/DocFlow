package at.fhtw.docflow.identity.persistence;

import at.fhtw.docflow.identity.application.OwnerDirectory;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class JpaOwnerDirectory implements OwnerDirectory {
    private final UserRepository repository;

    public JpaOwnerDirectory(UserRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Long> findIdByUsername(String username) {
        return repository.findByUsername(username.strip().toLowerCase(Locale.ROOT)).map(UserEntity::getId);
    }
}
