// Copyright (c) 2026 ElatusDev
package com.elatusdev.pokedex.domain.vo;

import com.elatusdev.pokedex.domain.exception.InvalidPokemonDataException;
import java.util.Locale;
import java.util.Objects;

public record PokemonName(String value) {

    public static final int MAX_LENGTH = 60;

    public PokemonName {
        Objects.requireNonNull(value, "name");
        value = value.strip();
        if (value.isEmpty() || value.length() > MAX_LENGTH) {
            throw new InvalidPokemonDataException("name must be 1.." + MAX_LENGTH + " characters");
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PokemonName that && value.equalsIgnoreCase(that.value);
    }

    @Override
    public int hashCode() {
        return value.toLowerCase(Locale.ROOT).hashCode();
    }
}
