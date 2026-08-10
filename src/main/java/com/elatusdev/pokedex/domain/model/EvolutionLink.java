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
        requireDistinctEndpoints(from, to);
        requireTrigger(trigger);
        requireMinLevel(minLevel);
    }

    // the weakest form of F12: a self-loop is a cycle the flattener can produce on its own
    private static void requireDistinctEndpoints(PokeApiId from, PokeApiId to) {
        if (from.equals(to)) {
            throw new InvalidPokemonDataException("a species cannot evolve into itself: " + from.value());
        }
    }

    private static void requireTrigger(String trigger) {
        if (trigger.isEmpty()) {
            throw new InvalidPokemonDataException("trigger must be non-blank");
        }
    }

    private static void requireMinLevel(Optional<Integer> minLevel) {
        if (minLevel.isPresent() && minLevel.get() <= 0) {
            throw new InvalidPokemonDataException("minLevel must be positive, was " + minLevel.get());
        }
    }
}
