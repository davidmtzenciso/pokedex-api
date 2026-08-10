package com.elatusdev.pokedex.identity.domain.model;

import java.time.Instant;
import java.util.Objects;

// The jti and the expiry come back with the token because the caller has to store them: a
// refresh jti becomes a family member, an access jti becomes a session, and neither can be
// recovered from the signed string without parsing it back.
public record IssuedToken(String token, String jti, Instant expiresAt) {

    public IssuedToken {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(jti, "jti");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
