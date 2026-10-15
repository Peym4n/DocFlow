package at.fhtw.docflow.identity.application;

import java.util.Optional;

public interface OwnerDirectory {
    Optional<Long> findIdByUsername(String username);
}
