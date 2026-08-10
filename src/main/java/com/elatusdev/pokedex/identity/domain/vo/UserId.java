package com.elatusdev.pokedex.identity.domain.vo;

import com.elatusdev.pokedex.shared.domain.exception.InvalidPokemonDataException;

public record UserId(long value) {
    public UserId {
        if (value <= 0) {
            throw new InvalidPokemonDataException("userId must be positive, was " + value);
        }
    }

    public static UserId of(long value) {
        return new UserId(value);
    }
}
