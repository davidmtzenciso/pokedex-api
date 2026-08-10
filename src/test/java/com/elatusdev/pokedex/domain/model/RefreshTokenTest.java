// Copyright (c) 2026 ElatusDev
package com.elatusdev.pokedex.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.elatusdev.pokedex.domain.exception.InvalidPokemonDataException;
import com.elatusdev.pokedex.domain.vo.RefreshTokenId;
import com.elatusdev.pokedex.domain.vo.UserId;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

    private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");
    private static final Instant EXPIRES = NOW.plusSeconds(604_800);
    private static final UserId OWNER = UserId.of(7);
    private static final String FAMILY = "f3b1c0de-0000-4000-8000-000000000001";
    private static final String JTI = "9a7c1e22-0000-4000-8000-000000000002";

    private static RefreshToken issued() {
        return RefreshToken.issue(OWNER, FAMILY, JTI, EXPIRES);
    }

    @Test
    void should_carry_its_family_and_owner_when_issued() {
        RefreshToken token = issued();

        assertThat(token.id()).isEmpty();
        assertThat(token.userId()).isEqualTo(OWNER);
        assertThat(token.familyId()).isEqualTo(FAMILY);
        assertThat(token.jti()).isEqualTo(JTI);
        assertThat(token.expiresAt()).isEqualTo(EXPIRES);
        assertThat(token.revokedAt()).isEmpty();
    }

    @Test
    void should_carry_its_identity_when_rehydrated() {
        RefreshToken token =
                RefreshToken.rehydrate(RefreshTokenId.of(42), OWNER, FAMILY, JTI, EXPIRES, Optional.of(NOW));

        assertThat(token.id()).contains(RefreshTokenId.of(42));
        assertThat(token.revokedAt()).contains(NOW);
    }

    @Test
    void should_be_live_when_it_is_neither_revoked_nor_expired() {
        RefreshToken token = issued();

        assertThat(token.isLive(NOW)).isTrue();
        assertThat(token.isRevoked()).isFalse();
        assertThat(token.isExpired(NOW)).isFalse();
    }

    @Test
    void should_not_be_live_when_it_has_been_revoked() {
        RefreshToken token = issued().revoke(NOW);

        assertThat(token.isRevoked()).isTrue();
        assertThat(token.isLive(NOW)).isFalse();
        assertThat(token.revokedAt()).contains(NOW);
    }

    // the boundary is the whole point of an expiry: at exactly expiresAt the token is spent
    @Test
    void should_not_be_live_at_the_instant_it_expires() {
        RefreshToken token = issued();

        assertThat(token.isExpired(EXPIRES.minusMillis(1))).isFalse();
        assertThat(token.isExpired(EXPIRES)).isTrue();
        assertThat(token.isLive(EXPIRES)).isFalse();
        assertThat(token.isExpired(EXPIRES.plusMillis(1))).isTrue();
    }

    // a second revocation must not rewrite when the theft was detected — the first instant
    // is the audit record, and family revocation revokes tokens that are already revoked
    @Test
    void should_keep_the_original_instant_when_it_is_revoked_twice() {
        RefreshToken once = issued().revoke(NOW);

        RefreshToken twice = once.revoke(NOW.plusSeconds(60));

        assertThat(twice.revokedAt()).contains(NOW);
        assertThat(twice).isSameAs(once);
    }

    @Test
    void should_leave_the_original_untouched_when_revoked() {
        RefreshToken original = issued();

        original.revoke(NOW);

        assertThat(original.revokedAt()).isEmpty();
    }

    @Test
    void should_reject_a_blank_family_identifier() {
        assertThatThrownBy(() -> RefreshToken.issue(OWNER, " ", JTI, EXPIRES))
                .isInstanceOf(InvalidPokemonDataException.class)
                .hasMessageContaining("familyId");
    }

    @Test
    void should_reject_a_blank_jti() {
        assertThatThrownBy(() -> RefreshToken.issue(OWNER, FAMILY, "", EXPIRES))
                .isInstanceOf(InvalidPokemonDataException.class)
                .hasMessageContaining("jti");
    }

    @Test
    void should_reject_a_missing_owner() {
        assertThatThrownBy(() -> RefreshToken.issue(null, FAMILY, JTI, EXPIRES))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void should_reject_a_missing_expiry() {
        assertThatThrownBy(() -> RefreshToken.issue(OWNER, FAMILY, JTI, null))
                .isInstanceOf(NullPointerException.class);
    }
}
