package com.elatusdev.pokedex.shared.domain.vo;

import com.elatusdev.pokedex.shared.domain.exception.InvalidPokemonDataException;

public record PokeApiId(int value) {
    public PokeApiId {
        if (value <= 0) {
            throw new InvalidPokemonDataException("pokeApiId must be positive, was " + value);
        }
    }

    public static PokeApiId of(int value) {
        return new PokeApiId(value);
    }
}
