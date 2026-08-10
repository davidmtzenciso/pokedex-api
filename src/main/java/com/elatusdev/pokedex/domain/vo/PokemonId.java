// Copyright (c) 2026 ElatusDev
package com.elatusdev.pokedex.domain.vo;

import com.elatusdev.pokedex.domain.exception.InvalidPokemonDataException;

public record PokemonId(long value) {
    public PokemonId {
        if (value <= 0) {
            throw new InvalidPokemonDataException("pokemonId must be positive, was " + value);
        }
    }

    public static PokemonId of(long value) {
        return new PokemonId(value);
    }
}
