package com.elatusdev.pokedex.identity.domain.vo;

import com.elatusdev.pokedex.shared.domain.exception.InvalidPokemonDataException;

public record RefreshTokenId(long value) {
    public RefreshTokenId {
        if (value <= 0) {
            throw new InvalidPokemonDataException("refreshTokenId must be positive, was " + value);
        }
    }

    public static RefreshTokenId of(long value) {
        return new RefreshTokenId(value);
    }
}
