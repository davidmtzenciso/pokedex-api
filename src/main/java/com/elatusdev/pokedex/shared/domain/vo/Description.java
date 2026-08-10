package com.elatusdev.pokedex.shared.domain.vo;

import com.elatusdev.pokedex.shared.domain.exception.InvalidPokemonDataException;
import java.util.Objects;

public record Description(String value) {

    public static final int MAX_LENGTH = 2000;

    public Description {
        Objects.requireNonNull(value, "description");
        value = normalise(value);
        if (value.isEmpty() || value.length() > MAX_LENGTH) {
            throw new InvalidPokemonDataException("description must be 1.." + MAX_LENGTH + " characters");
        }
    }

    private static String normalise(String raw) {
        return raw.replaceAll("[\\n\\f\\r\\t\\u000b]", " ").replaceAll(" {2,}", " ").strip();
    }
}
