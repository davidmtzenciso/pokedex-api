package com.elatusdev.pokedex.pokedex.domain;


public class PokemonNotFoundException extends RuntimeException {

    private final transient PokemonId id;

    public PokemonNotFoundException(PokemonId id) {
        super("No Pokemon with id " + id.value());
        this.id = id;
    }

    // Sync looks a record up by an upstream id or a name, and finds neither locally nor
    // upstream. catalog has its own PokemonNotFoundUpstreamException for the read path, but
    // naming it from pokedex would close the CY1 cycle — so replication reports the miss in
    // its own context's terms. Same 404, same POKEMON_NOT_FOUND code.
    public PokemonNotFoundException(String idOrName) {
        super("No Pokemon upstream for '" + idOrName + "'");
        this.id = null;
    }

    public PokemonId id() {
        return id;
    }
}
