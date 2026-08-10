// Copyright (c) 2026 ElatusDev
package com.elatusdev.pokedex.domain.vo;

import com.elatusdev.pokedex.domain.exception.InvalidPokemonDataException;

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
