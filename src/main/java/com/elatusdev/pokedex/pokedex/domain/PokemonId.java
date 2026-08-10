package com.elatusdev.pokedex.pokedex.domain;

import com.elatusdev.pokedex.shared.domain.InvalidPokemonDataException;

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
