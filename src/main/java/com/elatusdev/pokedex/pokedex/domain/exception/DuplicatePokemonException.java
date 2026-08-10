package com.elatusdev.pokedex.pokedex.domain.exception;

import com.elatusdev.pokedex.shared.domain.vo.PokeApiId;
import com.elatusdev.pokedex.pokedex.domain.model.Pokemon;

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
