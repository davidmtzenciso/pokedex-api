package com.elatusdev.pokedex.pokedex.domain;


public class PokemonNotFoundException extends RuntimeException {

    private final transient PokemonId id;

    public PokemonNotFoundException(PokemonId id) {
        super("No Pokemon with id " + id.value());
        this.id = id;
    }

    public PokemonId id() {
        return id;
    }
}
