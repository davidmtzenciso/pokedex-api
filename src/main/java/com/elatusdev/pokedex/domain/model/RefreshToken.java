package com.elatusdev.pokedex.domain.model;

import com.elatusdev.pokedex.domain.exception.InvalidPokemonDataException;
import com.elatusdev.pokedex.domain.vo.RefreshTokenId;
import com.elatusdev.pokedex.domain.vo.UserId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

// The child of the User aggregate that makes token theft containable. It stores the jti of
// a refresh token, never the token itself: the signed string is a bearer credential, and a
// store that holds it hands an attacker every session it protects.
public record RefreshToken(
        Optional<RefreshTokenId> id,
        UserId userId,
        String familyId,
        String jti,
        Instant expiresAt,
        Optional<Instant> revokedAt) {

    public RefreshToken {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(revokedAt, "revokedAt");
        requirePresent(familyId, "familyId");
        requirePresent(jti, "jti");
    }

    public static RefreshToken issue(UserId userId, String familyId, String jti, Instant expiresAt) {
        return new RefreshToken(Optional.empty(), userId, familyId, jti, expiresAt, Optional.empty());
    }

    public static RefreshToken rehydrate(
            RefreshTokenId id,
            UserId userId,
            String familyId,
            String jti,
            Instant expiresAt,
            Optional<Instant> revokedAt) {
        return new RefreshToken(Optional.of(id), userId, familyId, jti, expiresAt, revokedAt);
    }

    public boolean isRevoked() {
        return revokedAt.isPresent();
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public boolean isLive(Instant now) {
        return !isRevoked() && !isExpired(now);
    }

    // idempotent: family revocation sweeps tokens that are already revoked, and the first
    // instant is the audit record of when the theft was detected
    public RefreshToken revoke(Instant at) {
        Objects.requireNonNull(at, "at");
        return isRevoked() ? this : new RefreshToken(id, userId, familyId, jti, expiresAt, Optional.of(at));
    }

    private static void requirePresent(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InvalidPokemonDataException(field + " must not be blank");
        }
    }
}
