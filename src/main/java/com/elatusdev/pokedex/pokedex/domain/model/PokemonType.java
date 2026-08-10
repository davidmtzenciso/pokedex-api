package com.elatusdev.pokedex.pokedex.domain.model;

import com.elatusdev.pokedex.shared.domain.exception.InvalidPokemonDataException;
import java.util.Locale;
import java.util.Objects;

public record PokemonType(String name, int slot) {

    public PokemonType {
        Objects.requireNonNull(name, "type name");
        name = name.strip().toLowerCase(Locale.ROOT);
        if (name.isEmpty() || slot < 1) {
            throw new InvalidPokemonDataException(
                    "type name must be non-blank and slot must be positive, was '" + name + "' slot " + slot);
        }
    }
}
