package com.elatusdev.pokedex.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.elatusdev.pokedex.domain.exception.InvalidPokemonDataException;
import com.elatusdev.pokedex.domain.exception.UserAlreadyExistsException;
import com.elatusdev.pokedex.domain.model.Role;
import com.elatusdev.pokedex.domain.model.User;
import com.elatusdev.pokedex.domain.port.ClockPort;
import com.elatusdev.pokedex.domain.port.PasswordHasher;
import com.elatusdev.pokedex.domain.port.UserRepository;
import com.elatusdev.pokedex.domain.vo.Email;
import com.elatusdev.pokedex.domain.vo.PasswordHash;
import com.elatusdev.pokedex.domain.vo.UserId;
import com.elatusdev.pokedex.domain.vo.Username;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegisterUserUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");
    private static final String RAW = "Demo123!correct-horse";
    private static final PasswordHash HASH = new PasswordHash("$2a$12$hashed");
    private static final Username USERNAME = new Username("demo");
    private static final Email EMAIL = new Email("demo@elatus-dev.com");

    @Mock
    private UserRepository users;

    @Mock
    private PasswordHasher hasher;

    @Mock
    private ClockPort clock;

    private RegisterUserUseCase useCase() {
        return new RegisterUserUseCase(users, hasher, clock);
    }

    // User is an aggregate and deliberately has no equals(): identity, not value semantics.
    // The predicate covers every field, so it is exactly as strict as equality would be —
    // this is not an any() matcher in disguise.
    private static User newCurator() {
        return argThat(user -> user.id().isEmpty()
                && USERNAME.equals(user.username())
                && EMAIL.equals(user.email())
                && HASH.equals(user.passwordHash())
                && Set.of(Role.CURATOR).equals(user.roles())
                && NOW.equals(user.createdAt()));
    }

    @Test
    void should_store_the_user_with_a_hashed_password_and_the_curator_role() {
        User saved = User.rehydrate(UserId.of(1), USERNAME, EMAIL, HASH, Set.of(Role.CURATOR), NOW);
        when(users.existsByUsername(USERNAME)).thenReturn(false);
        when(users.existsByEmail(EMAIL)).thenReturn(false);
        when(hasher.hash(RAW)).thenReturn(HASH);
        when(clock.now()).thenReturn(NOW);
        when(users.save(newCurator())).thenReturn(saved);

        User result = useCase().register("demo", "demo@elatus-dev.com", RAW);

        assertThat(result).isEqualTo(saved);
        verify(users, times(1)).existsByUsername(USERNAME);
        verify(users, times(1)).existsByEmail(EMAIL);
        verify(users, times(1)).save(newCurator());
        verify(hasher, times(1)).hash(RAW);
        verify(clock, times(1)).now();
        verifyNoMoreInteractions(users, hasher, clock);
    }

    // privilege escalation by self-registration: ADMIN is granted, never claimed
    @Test
    void should_never_grant_the_admin_role_on_registration() {
        when(users.existsByUsername(USERNAME)).thenReturn(false);
        when(users.existsByEmail(EMAIL)).thenReturn(false);
        when(hasher.hash(RAW)).thenReturn(HASH);
        when(clock.now()).thenReturn(NOW);
        when(users.save(newCurator()))
                .thenReturn(User.rehydrate(UserId.of(1), USERNAME, EMAIL, HASH, Set.of(Role.CURATOR), NOW));

        User result = useCase().register("demo", "demo@elatus-dev.com", RAW);

        assertThat(result.roles()).containsExactly(Role.CURATOR);
        assertThat(result.hasRole(Role.ADMIN)).isFalse();
    }

    @Test
    void should_normalise_the_username_and_email_before_storing_them() {
        when(users.existsByUsername(USERNAME)).thenReturn(false);
        when(users.existsByEmail(EMAIL)).thenReturn(false);
        when(hasher.hash(RAW)).thenReturn(HASH);
        when(clock.now()).thenReturn(NOW);
        when(users.save(newCurator()))
                .thenReturn(User.rehydrate(UserId.of(1), USERNAME, EMAIL, HASH, Set.of(Role.CURATOR), NOW));

        useCase().register("  DEMO  ", "  Demo@Elatus-Dev.com ", RAW);

        verify(users, times(1)).existsByUsername(USERNAME);
        verify(users, times(1)).existsByEmail(EMAIL);
    }

    @Test
    void should_reject_a_username_that_is_already_taken() {
        when(users.existsByUsername(USERNAME)).thenReturn(true);

        assertThatThrownBy(() -> useCase().register("demo", "demo@elatus-dev.com", RAW))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasFieldOrPropertyWithValue("field", "username");

        // nothing is hashed and nothing is written when the name is taken
        verify(users, times(1)).existsByUsername(USERNAME);
        verifyNoMoreInteractions(users);
        verifyNoInteractions(hasher, clock);
    }

    @Test
    void should_reject_an_email_that_is_already_taken() {
        when(users.existsByUsername(USERNAME)).thenReturn(false);
        when(users.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> useCase().register("demo", "demo@elatus-dev.com", RAW))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasFieldOrPropertyWithValue("field", "email");

        verify(users, times(1)).existsByUsername(USERNAME);
        verify(users, times(1)).existsByEmail(EMAIL);
        verifyNoMoreInteractions(users);
        verifyNoInteractions(hasher, clock);
    }

    @Test
    void should_reject_a_malformed_email_before_touching_the_store() {
        assertThatThrownBy(() -> useCase().register("demo", "not-an-address", RAW))
                .isInstanceOf(InvalidPokemonDataException.class);

        verifyNoInteractions(users, hasher, clock);
    }

    @Test
    void should_reject_a_malformed_username_before_touching_the_store() {
        assertThatThrownBy(() -> useCase().register("no", "demo@elatus-dev.com", RAW))
                .isInstanceOf(InvalidPokemonDataException.class);

        verifyNoInteractions(users, hasher, clock);
    }
}
