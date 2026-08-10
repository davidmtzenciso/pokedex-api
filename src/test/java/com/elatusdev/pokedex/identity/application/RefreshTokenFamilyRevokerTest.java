package com.elatusdev.pokedex.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.elatusdev.pokedex.identity.domain.RefreshToken;
import com.elatusdev.pokedex.identity.domain.RefreshTokenId;
import com.elatusdev.pokedex.identity.domain.RefreshTokenRepository;
import com.elatusdev.pokedex.identity.domain.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// The class had no test of its own — it was only reached through the rotation use case,
// which never looked at what it returned. That left the revoked count unasserted, and a
// mutant replacing it with 0 survived: the security log would have reported "revoked 0
// tokens" on every detected theft while the revocation itself still happened.
@ExtendWith(MockitoExtension.class)
class RefreshTokenFamilyRevokerTest {

    private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");
    private static final Instant EXPIRES = NOW.plusSeconds(604_800);
    private static final UserId OWNER = UserId.of(7);
    private static final String FAMILY = "family-1";

    @Mock
    private RefreshTokenRepository refreshTokens;

    private RefreshTokenFamilyRevoker revoker() {
        return new RefreshTokenFamilyRevoker(refreshTokens);
    }

    private static RefreshToken member(long id, String jti, Optional<Instant> revokedAt) {
        return RefreshToken.rehydrate(RefreshTokenId.of(id), OWNER, FAMILY, jti, EXPIRES, revokedAt);
    }

    // I8 / F11 — after this runs, the family holds no live token at all
    @Test
    void should_revoke_every_token_in_the_family_and_report_how_many() {
        when(refreshTokens.findByFamilyId(FAMILY))
                .thenReturn(List.of(
                        member(1, "jti-1", Optional.empty()),
                        member(2, "jti-2", Optional.empty()),
                        member(3, "jti-3", Optional.empty())));

        int revoked = revoker().revokeFamily(FAMILY, NOW);

        assertThat(revoked).isEqualTo(3);
        verify(refreshTokens, times(1)).findByFamilyId(FAMILY);
        verify(refreshTokens, times(1))
                .saveAll(List.of(
                        member(1, "jti-1", Optional.of(NOW)),
                        member(2, "jti-2", Optional.of(NOW)),
                        member(3, "jti-3", Optional.of(NOW))));
        verifyNoMoreInteractions(refreshTokens);
    }

    // an already-revoked token keeps its original instant: that timestamp records when the
    // token was legitimately spent, and overwriting it loses the audit trail
    @Test
    void should_keep_the_original_instant_on_a_token_that_was_already_revoked() {
        Instant earlier = NOW.minusSeconds(30);
        when(refreshTokens.findByFamilyId(FAMILY))
                .thenReturn(List.of(member(1, "jti-1", Optional.of(earlier)), member(2, "jti-2", Optional.empty())));

        int revoked = revoker().revokeFamily(FAMILY, NOW);

        assertThat(revoked).isEqualTo(2);
        verify(refreshTokens, times(1))
                .saveAll(List.of(member(1, "jti-1", Optional.of(earlier)), member(2, "jti-2", Optional.of(NOW))));
    }

    @Test
    void should_report_nothing_revoked_when_the_family_is_unknown() {
        when(refreshTokens.findByFamilyId(FAMILY)).thenReturn(List.of());

        assertThat(revoker().revokeFamily(FAMILY, NOW)).isZero();

        verify(refreshTokens, times(1)).saveAll(List.of());
    }
}
