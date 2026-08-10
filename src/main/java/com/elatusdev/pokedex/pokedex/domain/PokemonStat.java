package com.elatusdev.pokedex.pokedex.domain;

import com.elatusdev.pokedex.shared.domain.InvalidPokemonDataException;
import java.util.Locale;
import java.util.Objects;

public record PokemonStat(String name, int baseValue, int effort) {

    public PokemonStat {
        Objects.requireNonNull(name, "stat name");
        name = name.strip().toLowerCase(Locale.ROOT);
        if (name.isEmpty() || baseValue < 0 || effort < 0) {
            throw new InvalidPokemonDataException("stat name must be non-blank and baseValue and effort must not be negative, was '"
                    + name + "' baseValue " + baseValue + " effort " + effort);
        }
    }
}
