package com.elatusdev.pokedex.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.elatusdev.pokedex.identity.domain.InvalidTokenException;
import com.elatusdev.pokedex.identity.domain.Role;
import com.elatusdev.pokedex.identity.domain.User;
import com.elatusdev.pokedex.identity.domain.SessionStore;
import com.elatusdev.pokedex.identity.domain.UserRepository;
import com.elatusdev.pokedex.identity.domain.Email;
import com.elatusdev.pokedex.identity.domain.PasswordHash;
import com.elatusdev.pokedex.identity.domain.UserId;
import com.elatusdev.pokedex.identity.domain.Username;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");
    private static final UserId ID = UserId.of(7);
    private static final String JTI = "jti-1";

    private static final User DEMO = User.rehydrate(
            ID,
            new Username("demo"),
            new Email("demo@elatus-dev.com"),
            new PasswordHash("$2a$12$stored"),
            Set.of(Role.CURATOR),
            NOW);

    @Mock
    private SessionStore sessions;

    @Mock
    private UserRepository users;

    @Nested
    class Logout {

        @Test
        void should_close_the_session_of_the_presented_token() {
            new LogoutUseCase(sessions).logout(JTI);

            verify(sessions, times(1)).close(JTI);
            verifyNoMoreInteractions(sessions);
        }
    }

    @Nested
    class CurrentUser {

        @Test
        void should_return_the_principal_behind_the_token() {
            when(users.findById(ID)).thenReturn(Optional.of(DEMO));

            assertThat(new GetCurrentUserUseCase(users).currentUser(ID)).isEqualTo(DEMO);

            verify(users, times(1)).findById(ID);
            verifyNoMoreInteractions(users);
        }

        // not a 404: the credential is what is wrong, and a 404 would confirm which ids
        // were once real
        @Test
        void should_reject_a_token_whose_subject_no_longer_exists() {
            when(users.findById(ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> new GetCurrentUserUseCase(users).currentUser(ID))
                    .isInstanceOf(InvalidTokenException.class);
        }
    }
}
