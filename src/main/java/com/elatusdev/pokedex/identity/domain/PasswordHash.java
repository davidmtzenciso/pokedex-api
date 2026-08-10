package com.elatusdev.pokedex.identity.domain;

import com.elatusdev.pokedex.shared.domain.InvalidPokemonDataException;
import java.util.Objects;

public record PasswordHash(String value) {

    public static final String MASK = "***";

    public PasswordHash {
        Objects.requireNonNull(value, "passwordHash");
        if (value.isBlank()) {
            throw new InvalidPokemonDataException("passwordHash must not be blank");
        }
    }

    @Override
    public String toString() {
        return MASK;
    }
}
