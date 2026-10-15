package at.fhtw.docflow.identity.api;

import at.fhtw.docflow.identity.application.OwnerDirectory;
import at.fhtw.docflow.identity.application.OwnerRequiredException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PrincipalCurrentOwnerTest {
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final OwnerDirectory owners = mock(OwnerDirectory.class);
    private final PrincipalCurrentOwner currentOwner = new PrincipalCurrentOwner(request, owners);

    @Test
    void rejectsMissingPrincipalEvenWhenClientSuppliesOwnerHints() {
        request.addHeader("X-Owner-Id", "7");
        request.addHeader("X-User", "alice");
        request.addParameter("ownerId", "7");
        assertThatThrownBy(currentOwner::requireOwnerId).isInstanceOf(OwnerRequiredException.class);
        verifyNoInteractions(owners);
    }

    @Test
    void resolvesOnlyTheTrustedPrincipal() {
        request.setUserPrincipal(() -> "alice");
        when(owners.findIdByUsername("alice")).thenReturn(Optional.of(7L));
        assertThat(currentOwner.requireOwnerId()).isEqualTo(7L);
    }

    @Test
    void rejectsUnknownPrincipal() {
        request.setUserPrincipal(() -> "unknown");
        when(owners.findIdByUsername("unknown")).thenReturn(Optional.empty());
        assertThatThrownBy(currentOwner::requireOwnerId).isInstanceOf(OwnerRequiredException.class);
    }
}
