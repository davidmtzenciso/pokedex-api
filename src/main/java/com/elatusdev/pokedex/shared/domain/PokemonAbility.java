package com.elatusdev.pokedex.shared.domain;

import java.util.Locale;
import java.util.Objects;

public record PokemonAbility(String name, int slot, boolean hidden) {

    public PokemonAbility {
        Objects.requireNonNull(name, "ability name");
        name = name.strip().toLowerCase(Locale.ROOT);
        if (name.isEmpty() || slot < 1) {
            throw new InvalidPokemonDataException(
                    "ability name must be non-blank and slot must be positive, was '" + name + "' slot " + slot);
        }
    }
}
