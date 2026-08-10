package com.elatusdev.pokedex.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.elatusdev.pokedex.domain.exception.InvalidTokenException;
import com.elatusdev.pokedex.domain.exception.TokenReuseDetectedException;
import com.elatusdev.pokedex.domain.model.IssuedToken;
import com.elatusdev.pokedex.domain.model.RefreshToken;
import com.elatusdev.pokedex.domain.model.Role;
import com.elatusdev.pokedex.domain.model.TokenType;
import com.elatusdev.pokedex.domain.model.User;
import com.elatusdev.pokedex.domain.model.VerifiedToken;
import com.elatusdev.pokedex.domain.port.ClockPort;
import com.elatusdev.pokedex.domain.port.RefreshTokenRepository;
import com.elatusdev.pokedex.domain.port.SessionStore;
import com.elatusdev.pokedex.domain.port.TokenIssuer;
import com.elatusdev.pokedex.domain.port.UserRepository;
import com.elatusdev.pokedex.domain.vo.Email;
import com.elatusdev.pokedex.domain.vo.PasswordHash;
import com.elatusdev.pokedex.domain.vo.RefreshTokenId;
import com.elatusdev.pokedex.domain.vo.UserId;
import com.elatusdev.pokedex.domain.vo.Username;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenRotationTest {

    private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");
    private static final Instant REFRESH_EXPIRY = NOW.plusSeconds(604_800);
    private static final Instant ACCESS_EXPIRY = NOW.plusSeconds(900);

    private static final UserId OWNER = UserId.of(7);
    private static final String FAMILY = "family-1";
    private static final String PRESENTED = "presented.refresh.jwt";
    private static final String OLD_JTI = "jti-old";
    private static final String NEW_REFRESH_JTI = "jti-new-refresh";
    private static final String NEW_ACCESS_JTI = "jti-new-access";

    private static final IssuedToken NEW_ACCESS = new IssuedToken("new.access.jwt", NEW_ACCESS_JTI, ACCESS_EXPIRY);
    private static final IssuedToken NEW_REFRESH =
            new IssuedToken("new.refresh.jwt", NEW_REFRESH_JTI, REFRESH_EXPIRY);

    private static final User OWNER_USER = User.rehydrate(
            OWNER,
            new Username("demo"),
            new Email("demo@elatus-dev.com"),
            new PasswordHash("$2a$12$hash"),
            Set.of(Role.CURATOR),
            NOW.minusSeconds(86_400));

    @Mock
    private RefreshTokenRepository refreshTokens;

    @Mock
    private UserRepository users;

    @Mock
    private TokenIssuer tokenIssuer;

    @Mock
    private SessionStore sessions;

    @Mock
    private ClockPort clock;

    private RefreshTokenRotationUseCase useCase() {
        return new RefreshTokenRotationUseCase(refreshTokens, users, tokenIssuer, sessions, clock);
    }

    private static VerifiedToken verifiedRefresh(String jti) {
        return new VerifiedToken(TokenType.REFRESH, OWNER, jti, Set.of(), Optional.of(FAMILY), REFRESH_EXPIRY);
    }

    private static RefreshToken stored(long id, String jti, Optional<Instant> revokedAt) {
        return RefreshToken.rehydrate(RefreshTokenId.of(id), OWNER, FAMILY, jti, REFRESH_EXPIRY, revokedAt);
    }

    @Test
    void should_issue_a_successor_in_the_same_family_when_the_presented_token_is_live() {
        when(clock.now()).thenReturn(NOW);
        when(tokenIssuer.verify(PRESENTED, NOW)).thenReturn(Optional.of(verifiedRefresh(OLD_JTI)));
        when(refreshTokens.findByJti(OLD_JTI)).thenReturn(Optional.of(stored(1, OLD_JTI, Optional.empty())));
        when(users.findById(OWNER)).thenReturn(Optional.of(OWNER_USER));
        when(tokenIssuer.issueRefreshToken(OWNER, FAMILY, NOW)).thenReturn(NEW_REFRESH);
        when(tokenIssuer.issueAccessToken(OWNER, Set.of(Role.CURATOR), NOW)).thenReturn(NEW_ACCESS);

        TokenPair pair = useCase().rotate(PRESENTED);

        assertThat(pair).isEqualTo(new TokenPair("new.access.jwt", "new.refresh.jwt", 900L));
        verify(clock, times(1)).now();
        verify(tokenIssuer, times(1)).verify(PRESENTED, NOW);
        verify(refreshTokens, times(1)).findByJti(OLD_JTI);
        verify(users, times(1)).findById(OWNER);
        // the presented token is spent before its successor exists
        verify(refreshTokens, times(1)).save(stored(1, OLD_JTI, Optional.of(NOW)));
        verify(refreshTokens, times(1)).save(RefreshToken.issue(OWNER, FAMILY, NEW_REFRESH_JTI, REFRESH_EXPIRY));
        verify(tokenIssuer, times(1)).issueRefreshToken(OWNER, FAMILY, NOW);
        verify(tokenIssuer, times(1)).issueAccessToken(OWNER, Set.of(Role.CURATOR), NOW);
        verify(sessions, times(1)).open(NEW_ACCESS_JTI, OWNER, ACCESS_EXPIRY);
        verifyNoMoreInteractions(refreshTokens, users, tokenIssuer, sessions, clock);
    }

    // I8 / F11 — after a rotation the family holds exactly one live token
    @Test
    void should_leave_exactly_one_live_token_in_the_family_when_it_rotates() {
        when(clock.now()).thenReturn(NOW);
        when(tokenIssuer.verify(PRESENTED, NOW)).thenReturn(Optional.of(verifiedRefresh(OLD_JTI)));
        when(refreshTokens.findByJti(OLD_JTI)).thenReturn(Optional.of(stored(1, OLD_JTI, Optional.empty())));
        when(users.findById(OWNER)).thenReturn(Optional.of(OWNER_USER));
        when(tokenIssuer.issueRefreshToken(OWNER, FAMILY, NOW)).thenReturn(NEW_REFRESH);
        when(tokenIssuer.issueAccessToken(OWNER, Set.of(Role.CURATOR), NOW)).thenReturn(NEW_ACCESS);

        useCase().rotate(PRESENTED);

        RefreshToken spent = stored(1, OLD_JTI, Optional.of(NOW));
        RefreshToken successor = RefreshToken.issue(OWNER, FAMILY, NEW_REFRESH_JTI, REFRESH_EXPIRY);
        assertThat(List.of(spent, successor).stream().filter(token -> token.isLive(NOW)).toList())
                .containsExactly(successor);
    }

    // AC-AUTH-2 — replay is the signature of theft, and the whole family goes down with it
    @Test
    void should_revoke_the_entire_family_when_an_already_rotated_token_is_replayed() {
        RefreshToken replayed = stored(1, OLD_JTI, Optional.of(NOW.minusSeconds(30)));
        RefreshToken successor = stored(2, NEW_REFRESH_JTI, Optional.empty());
        when(clock.now()).thenReturn(NOW);
        when(tokenIssuer.verify(PRESENTED, NOW)).thenReturn(Optional.of(verifiedRefresh(OLD_JTI)));
        when(refreshTokens.findByJti(OLD_JTI)).thenReturn(Optional.of(replayed));
        when(refreshTokens.findByFamilyId(FAMILY)).thenReturn(List.of(replayed, successor));

        assertThatThrownBy(() -> useCase().rotate(PRESENTED))
                .isInstanceOf(TokenReuseDetectedException.class)
                .hasFieldOrPropertyWithValue("familyId", FAMILY);

        // synchronously, in the request that detected it — and the first revocation instant
        // is preserved, because it records when the token was legitimately spent
        verify(refreshTokens, times(1))
                .saveAll(List.of(stored(1, OLD_JTI, Optional.of(NOW.minusSeconds(30))), stored(2, NEW_REFRESH_JTI, Optional.of(NOW))));
        verify(refreshTokens, times(1)).findByJti(OLD_JTI);
        verify(refreshTokens, times(1)).findByFamilyId(FAMILY);
        verify(refreshTokens, never()).save(stored(1, OLD_JTI, Optional.of(NOW)));
        verify(tokenIssuer, times(1)).verify(PRESENTED, NOW);
        verifyNoMoreInteractions(refreshTokens, tokenIssuer);
        verifyNoInteractions(users, sessions);
    }

    @Test
    void should_leave_no_live_token_in_the_family_when_reuse_is_detected() {
        RefreshToken replayed = stored(1, OLD_JTI, Optional.of(NOW.minusSeconds(30)));
        RefreshToken successor = stored(2, NEW_REFRESH_JTI, Optional.empty());
        when(clock.now()).thenReturn(NOW);
        when(tokenIssuer.verify(PRESENTED, NOW)).thenReturn(Optional.of(verifiedRefresh(OLD_JTI)));
        when(refreshTokens.findByJti(OLD_JTI)).thenReturn(Optional.of(replayed));
        when(refreshTokens.findByFamilyId(FAMILY)).thenReturn(List.of(replayed, successor));

        assertThatThrownBy(() -> useCase().rotate(PRESENTED)).isInstanceOf(TokenReuseDetectedException.class);

        assertThat(List.of(replayed.revoke(NOW), successor.revoke(NOW)).stream()
                        .filter(token -> token.isLive(NOW))
                        .toList())
                .isEmpty();
    }

    @Test
    void should_reject_a_token_that_does_not_verify() {
        when(clock.now()).thenReturn(NOW);
        when(tokenIssuer.verify(PRESENTED, NOW)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().rotate(PRESENTED)).isInstanceOf(InvalidTokenException.class);

        verify(tokenIssuer, times(1)).verify(PRESENTED, NOW);
        verifyNoMoreInteractions(tokenIssuer);
        verifyNoInteractions(refreshTokens, users, sessions);
    }

    // token-type confusion: a 15-minute credential must not buy a 7-day one
    @Test
    void should_reject_an_access_token_presented_for_rotation() {
        when(clock.now()).thenReturn(NOW);
        when(tokenIssuer.verify(PRESENTED, NOW))
                .thenReturn(Optional.of(new VerifiedToken(
                        TokenType.ACCESS, OWNER, OLD_JTI, Set.of(Role.CURATOR), Optional.empty(), ACCESS_EXPIRY)));

        assertThatThrownBy(() -> useCase().rotate(PRESENTED)).isInstanceOf(InvalidTokenException.class);

        verify(tokenIssuer, times(1)).verify(PRESENTED, NOW);
        verifyNoMoreInteractions(tokenIssuer);
        verifyNoInteractions(refreshTokens, users, sessions);
    }

    @Test
    void should_reject_a_verified_token_that_the_store_has_never_seen() {
        when(clock.now()).thenReturn(NOW);
        when(tokenIssuer.verify(PRESENTED, NOW)).thenReturn(Optional.of(verifiedRefresh(OLD_JTI)));
        when(refreshTokens.findByJti(OLD_JTI)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().rotate(PRESENTED)).isInstanceOf(InvalidTokenException.class);

        verify(refreshTokens, times(1)).findByJti(OLD_JTI);
        verifyNoMoreInteractions(refreshTokens);
        verifyNoInteractions(users, sessions);
    }

    @Test
    void should_reject_an_expired_token_without_revoking_the_family() {
        RefreshToken expired = RefreshToken.rehydrate(
                RefreshTokenId.of(1), OWNER, FAMILY, OLD_JTI, NOW.minusSeconds(1), Optional.empty());
        when(clock.now()).thenReturn(NOW);
        when(tokenIssuer.verify(PRESENTED, NOW)).thenReturn(Optional.of(verifiedRefresh(OLD_JTI)));
        when(refreshTokens.findByJti(OLD_JTI)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> useCase().rotate(PRESENTED)).isInstanceOf(InvalidTokenException.class);

        // expiry is not theft — the family survives, the client simply logs in again
        verify(refreshTokens, times(1)).findByJti(OLD_JTI);
        verify(refreshTokens, never()).findByFamilyId(FAMILY);
        verifyNoMoreInteractions(refreshTokens);
        verifyNoInteractions(users, sessions);
    }

    @Test
    void should_reject_a_token_whose_owner_no_longer_exists() {
        when(clock.now()).thenReturn(NOW);
        when(tokenIssuer.verify(PRESENTED, NOW)).thenReturn(Optional.of(verifiedRefresh(OLD_JTI)));
        when(refreshTokens.findByJti(OLD_JTI)).thenReturn(Optional.of(stored(1, OLD_JTI, Optional.empty())));
        when(users.findById(OWNER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().rotate(PRESENTED)).isInstanceOf(InvalidTokenException.class);

        verify(users, times(1)).findById(OWNER);
        verify(refreshTokens, times(1)).findByJti(OLD_JTI);
        verifyNoMoreInteractions(users, refreshTokens);
        verifyNoInteractions(sessions);
    }
}
