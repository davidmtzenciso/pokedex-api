// Copyright (c) 2026 ElatusDev
package com.elatusdev.pokedex.domain.model;

import com.elatusdev.pokedex.domain.exception.InvalidPokemonDataException;
import com.elatusdev.pokedex.domain.vo.PokeApiId;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public record EvolutionLink(PokeApiId from, PokeApiId to, String trigger, Optional<Integer> minLevel) {

    public EvolutionLink {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(trigger, "trigger");
        Objects.requireNonNull(minLevel, "minLevel");
        trigger = trigger.strip().toLowerCase(Locale.ROOT);
        if (from.equals(to)) {
            throw new InvalidPokemonDataException("a species cannot evolve into itself: " + from.value());
        }
        if (trigger.isEmpty()) {
            throw new InvalidPokemonDataException("trigger must be non-blank");
        }
        if (minLevel.isPresent() && minLevel.get() <= 0) {
            throw new InvalidPokemonDataException("minLevel must be positive, was " + minLevel.get());
        }
    }
}
