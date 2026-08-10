package com.elatusdev.pokedex.domain.vo;

import com.elatusdev.pokedex.domain.exception.InvalidPokemonDataException;
import java.util.Objects;

public record PasswordHash(String value) {

    public PasswordHash {
        Objects.requireNonNull(value, "passwordHash");
        if (value.isBlank()) {
            throw new InvalidPokemonDataException("passwordHash must not be blank");
        }
    }

    @Override
    public String toString() {
        return "***";
    }
}
