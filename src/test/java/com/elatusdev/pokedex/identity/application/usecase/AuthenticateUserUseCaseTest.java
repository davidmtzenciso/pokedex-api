package com.elatusdev.pokedex.identity.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.elatusdev.pokedex.identity.domain.exception.InvalidCredentialsException;
import com.elatusdev.pokedex.identity.domain.model.IssuedToken;
import com.elatusdev.pokedex.identity.domain.model.RefreshToken;
import com.elatusdev.pokedex.identity.domain.model.Role;
import com.elatusdev.pokedex.identity.domain.model.User;
import com.elatusdev.pokedex.shared.port.ClockPort;
import com.elatusdev.pokedex.identity.domain.port.PasswordHasher;
import com.elatusdev.pokedex.identity.domain.port.RefreshTokenRepository;
import com.elatusdev.pokedex.identity.domain.port.SessionStore;
import com.elatusdev.pokedex.identity.domain.port.TokenIssuer;
import com.elatusdev.pokedex.identity.domain.port.UserRepository;
import com.elatusdev.pokedex.identity.domain.vo.Email;
import com.elatusdev.pokedex.identity.domain.vo.PasswordHash;
import com.elatusdev.pokedex.identity.domain.vo.UserId;
import com.elatusdev.pokedex.identity.domain.vo.Username;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthenticateUserUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");
    private static final Instant ACCESS_EXPIRY = NOW.plusSeconds(900);
    private static final Instant REFRESH_EXPIRY = NOW.plusSeconds(604_800);

    private static final UserId ID = UserId.of(7);
    private static final Username USERNAME = new Username("demo");
    private static final PasswordHash HASH = new PasswordHash("$2a$12$stored");
    private static final String RAW = "Demo123!";
    private static final String ACCESS_JTI = "jti-access";
    private static final String REFRESH_JTI = "jti-refresh";

    private static final User DEMO =
            User.rehydrate(ID, USERNAME, new Email("demo@elatus-dev.com"), HASH, Set.of(Role.CURATOR), NOW);

    private static final IssuedToken ACCESS = new IssuedToken("access.jwt", ACCESS_JTI, ACCESS_EXPIRY);
    private static final IssuedToken REFRESH = new IssuedToken("refresh.jwt", REFRESH_JTI, REFRESH_EXPIRY);

    @Mock
    private UserRepository users;

    @Mock
    private RefreshTokenRepository refreshTokens;

    @Mock
    private PasswordHasher hasher;

    @Mock
    private TokenIssuer tokenIssuer;

    @Mock
    private SessionStore sessions;

    @Mock
    private ClockPort clock;

    private AuthenticateUserUseCase useCase() {
        return new AuthenticateUserUseCase(users, refreshTokens, hasher, tokenIssuer, sessions, clock);
    }

    // the family id is generated per login and is not knowable to the test, so it is the one
    // component matched by shape; every other field is matched exactly
    private static String anyFamily() {
        return argThat(family -> family != null && !family.isBlank());
    }

    private static RefreshToken storedRefresh() {
        return argThat(token -> token.id().isEmpty()
                && ID.equals(token.userId())
                && REFRESH_JTI.equals(token.jti())
                && REFRESH_EXPIRY.equals(token.expiresAt())
                && token.revokedAt().isEmpty());
    }

    @Test
    void should_return_a_token_pair_and_open_a_session_when_the_password_matches() {
        when(clock.now()).thenReturn(NOW);
        when(users.findByUsername(USERNAME)).thenReturn(Optional.of(DEMO));
        when(hasher.matches(RAW, HASH)).thenReturn(true);
        when(tokenIssuer.issueAccessToken(ID, Set.of(Role.CURATOR), NOW)).thenReturn(ACCESS);
        when(tokenIssuer.issueRefreshToken(eq(ID), anyFamily(), eq(NOW))).thenReturn(REFRESH);

        TokenPair pair = useCase().authenticate("demo", RAW);

        assertThat(pair).isEqualTo(new TokenPair("access.jwt", "refresh.jwt", 900L));
        verify(sessions, times(1)).open(ACCESS_JTI, ID, ACCESS_EXPIRY);
        verify(refreshTokens, times(1)).save(storedRefresh());
        verify(users, times(1)).findByUsername(USERNAME);
        verify(hasher, times(1)).matches(RAW, HASH);
        verify(clock, times(1)).now();
        verify(tokenIssuer, times(1)).issueAccessToken(ID, Set.of(Role.CURATOR), NOW);
        verify(tokenIssuer, times(1)).issueRefreshToken(eq(ID), anyFamily(), eq(NOW));
        verifyNoMoreInteractions(users, refreshTokens, hasher, tokenIssuer, sessions, clock);
    }

    // AC: unknown user and wrong password are indistinguishable — same type, same message,
    // and the same amount of hashing work, so neither the body nor the clock is an oracle
    @Test
    void should_fail_identically_for_a_wrong_password() {
        when(clock.now()).thenReturn(NOW);
        when(users.findByUsername(USERNAME)).thenReturn(Optional.of(DEMO));
        when(hasher.matches(RAW, HASH)).thenReturn(false);

        assertThatThrownBy(() -> useCase().authenticate("demo", RAW))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid username or password");

        verify(hasher, times(1)).matches(RAW, HASH);
        verifyNoInteractions(tokenIssuer, sessions, refreshTokens);
    }

    @Test
    void should_fail_identically_for_an_unknown_user() {
        when(clock.now()).thenReturn(NOW);
        when(users.findByUsername(USERNAME)).thenReturn(Optional.empty());
        when(hasher.matches(RAW, AuthenticateUserUseCase.DECOY)).thenReturn(false);

        assertThatThrownBy(() -> useCase().authenticate("demo", RAW))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid username or password");

        // the decoy comparison is the point: skipping the hash for an unknown user makes the
        // response measurably faster, and that difference enumerates accounts
        verify(hasher, times(1)).matches(RAW, AuthenticateUserUseCase.DECOY);
        verifyNoInteractions(tokenIssuer, sessions, refreshTokens);
    }

    @Test
    void should_open_a_distinct_family_for_every_login() {
        when(clock.now()).thenReturn(NOW);
        when(users.findByUsername(USERNAME)).thenReturn(Optional.of(DEMO));
        when(hasher.matches(RAW, HASH)).thenReturn(true);
        when(tokenIssuer.issueAccessToken(ID, Set.of(Role.CURATOR), NOW)).thenReturn(ACCESS);
        when(tokenIssuer.issueRefreshToken(eq(ID), anyFamily(), eq(NOW))).thenReturn(REFRESH);
        AuthenticateUserUseCase useCase = useCase();

        useCase.authenticate("demo", RAW);
        useCase.authenticate("demo", RAW);

        // a second login must not join the first login's family: revoking one stolen session
        // would otherwise log the user out of every device they hold
        ArgumentCaptor<String> families = ArgumentCaptor.forClass(String.class);
        verify(tokenIssuer, times(2)).issueRefreshToken(eq(ID), families.capture(), eq(NOW));
        assertThat(families.getAllValues()).hasSize(2).doesNotHaveDuplicates();
    }
}
