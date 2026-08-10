// Copyright (c) 2026 ElatusDev
package com.elatusdev.pokedex.domain.vo;

import com.elatusdev.pokedex.domain.exception.InvalidPokemonDataException;
import java.util.Objects;

public record Category(String value) {

    public static final int MAX_LENGTH = 60;

    public Category {
        Objects.requireNonNull(value, "category");
        value = value.strip();
        if (value.isEmpty() || value.length() > MAX_LENGTH) {
            throw new InvalidPokemonDataException("category must be 1.." + MAX_LENGTH + " characters");
        }
    }
}
