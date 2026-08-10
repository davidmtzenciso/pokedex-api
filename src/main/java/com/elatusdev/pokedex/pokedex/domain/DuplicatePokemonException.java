package com.elatusdev.pokedex.pokedex.domain;

import com.elatusdev.pokedex.shared.domain.PokeApiId;

public class DuplicatePokemonException extends RuntimeException {

    private final transient PokeApiId pokeApiId;

    public DuplicatePokemonException(PokeApiId pokeApiId) {
        super("A Pokemon with pokeApiId " + pokeApiId.value() + " is already replicated");
        this.pokeApiId = pokeApiId;
    }

    public PokeApiId pokeApiId() {
        return pokeApiId;
    }
}
