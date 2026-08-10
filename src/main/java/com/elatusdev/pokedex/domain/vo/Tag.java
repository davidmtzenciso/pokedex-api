// Copyright (c) 2026 ElatusDev
package com.elatusdev.pokedex.domain.vo;

import com.elatusdev.pokedex.domain.exception.InvalidPokemonDataException;
import java.util.Locale;
import java.util.Objects;

public record Tag(String label) {

    public static final int MAX_LENGTH = 30;

    public Tag {
        Objects.requireNonNull(label, "tag");
        label = label.strip();
        if (label.isEmpty() || label.length() > MAX_LENGTH) {
            throw new InvalidPokemonDataException("tag must be 1.." + MAX_LENGTH + " characters");
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Tag that && label.equalsIgnoreCase(that.label);
    }

    @Override
    public int hashCode() {
        return label.toLowerCase(Locale.ROOT).hashCode();
    }
}
