// Copyright (c) 2026 ElatusDev
package com.elatusdev.pokedex.domain.exception;

public class InvalidPokemonDataException extends RuntimeException {
    public InvalidPokemonDataException(String message) {
        super(message);
    }
}
