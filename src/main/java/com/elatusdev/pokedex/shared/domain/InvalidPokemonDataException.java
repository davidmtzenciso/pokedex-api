package com.elatusdev.pokedex.shared.domain;

public class InvalidPokemonDataException extends RuntimeException {
    public InvalidPokemonDataException(String message) {
        super(message);
    }
}
