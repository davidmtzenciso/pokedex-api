package com.elatusdev.pokedex.catalog.domain;


public class PokemonNotFoundUpstreamException extends RuntimeException {

    private final transient String reference;

    public PokemonNotFoundUpstreamException(String reference) {
        super("PokeAPI has no Pokemon '" + reference + "'");
        this.reference = reference;
    }

    public String reference() {
        return reference;
    }
}
