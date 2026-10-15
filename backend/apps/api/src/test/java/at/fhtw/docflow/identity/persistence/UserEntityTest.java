package at.fhtw.docflow.identity.persistence;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class UserEntityTest {
    @Test
    void normalizesUsernameAndRejectsInvalidIdentifiers() {
        UserEntity user = new UserEntity("  Alice  ");
        assertThat(user.getUsername()).isEqualTo("alice");
        assertThat(user.getId()).isNull();
        for (String invalid : new String[]{null, "", " ", "user@example.test", "x".repeat(256)}) {
            assertThatThrownBy(() -> new UserEntity(invalid)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void directoryNormalizesPrincipalNameBeforeLookup() {
        UserRepository users = org.mockito.Mockito.mock(UserRepository.class);
        UserEntity user = org.mockito.Mockito.mock(UserEntity.class);
        org.mockito.Mockito.when(users.findByUsername("alice")).thenReturn(java.util.Optional.of(user));
        org.mockito.Mockito.when(user.getId()).thenReturn(7L);
        assertThat(new JpaOwnerDirectory(users).findIdByUsername(" ALICE ")).contains(7L);
        assertThat(new JpaOwnerDirectory(users).findIdByUsername("unknown")).isEmpty();
    }
}
