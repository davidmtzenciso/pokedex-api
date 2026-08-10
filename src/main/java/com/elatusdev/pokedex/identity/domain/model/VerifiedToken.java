package com.elatusdev.pokedex.identity.domain.model;

import com.elatusdev.pokedex.identity.domain.vo.UserId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

// What survives verification: signature, algorithm, issuer, audience and expiry have all
// been checked by the time one of these exists. It carries no PII, because the token it
// came from carries none.
public record VerifiedToken(
        TokenType type,
        UserId subject,
        String jti,
        Set<Role> roles,
        Optional<String> familyId,
        Instant expiresAt) {

    public VerifiedToken {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(jti, "jti");
        Objects.requireNonNull(familyId, "familyId");
        Objects.requireNonNull(expiresAt, "expiresAt");
        roles = Set.copyOf(Objects.requireNonNull(roles, "roles"));
    }
}
