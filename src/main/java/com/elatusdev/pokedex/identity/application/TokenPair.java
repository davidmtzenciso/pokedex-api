package com.elatusdev.pokedex.identity.application;

import com.elatusdev.pokedex.identity.domain.IssuedToken;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record TokenPair(String accessToken, String refreshToken, long expiresInSeconds) {

    public TokenPair {
        Objects.requireNonNull(accessToken, "accessToken");
        Objects.requireNonNull(refreshToken, "refreshToken");
    }

    public static TokenPair of(IssuedToken access, IssuedToken refresh, Instant now) {
        return new TokenPair(
                access.token(), refresh.token(), Duration.between(now, access.expiresAt()).toSeconds());
    }
}
