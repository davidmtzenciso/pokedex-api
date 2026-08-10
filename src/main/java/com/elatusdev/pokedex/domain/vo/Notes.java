// Copyright (c) 2026 ElatusDev
package com.elatusdev.pokedex.domain.vo;

import com.elatusdev.pokedex.domain.exception.InvalidPokemonDataException;
import java.util.Objects;

public record Notes(String value) {

    public static final int MAX_LENGTH = 2000;

    public Notes {
        Objects.requireNonNull(value, "notes");
        value = value.strip();
        if (value.isEmpty() || value.length() > MAX_LENGTH) {
            throw new InvalidPokemonDataException("notes must be 1.." + MAX_LENGTH + " characters");
        }
    }
}
