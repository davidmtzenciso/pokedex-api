package com.elatusdev.pokedex.identity.domain.vo;

import com.elatusdev.pokedex.shared.domain.exception.InvalidPokemonDataException;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record Username(String value) {

    private static final Pattern SHAPE = Pattern.compile("^[a-z0-9][a-z0-9._-]{2,29}$");

    public Username {
        Objects.requireNonNull(value, "username");
        value = value.strip().toLowerCase(Locale.ROOT);
        if (!SHAPE.matcher(value).matches()) {
            throw new InvalidPokemonDataException("username must be 3..30 chars of a-z, 0-9, dot, underscore or hyphen");
        }
    }
}
