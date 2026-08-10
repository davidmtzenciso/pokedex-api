// Copyright (c) 2026 ElatusDev
package com.elatusdev.pokedex.domain.vo;

import com.elatusdev.pokedex.domain.exception.InvalidPokemonDataException;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record Email(String value) {

    // possessive quantifiers cannot backtrack, so the domain part is matched label by label
    private static final Pattern SHAPE =
            Pattern.compile("^[^@\\s]++@[^@\\s.]++(?:\\.[^@\\s.]++)++$");

    public Email {
        Objects.requireNonNull(value, "email");
        value = value.strip().toLowerCase(Locale.ROOT);
        if (!SHAPE.matcher(value).matches()) {
            throw new InvalidPokemonDataException("email is not a valid address");
        }
    }
}
