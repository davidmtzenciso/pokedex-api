package com.elatusdev.pokedex.shared.domain.exception;

public class InvalidPokemonDataException extends RuntimeException {
    public InvalidPokemonDataException(String message) {
        super(message);
    }
}
