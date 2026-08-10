package com.elatusdev.pokedex.pokedex.domain;

import com.elatusdev.pokedex.shared.domain.InvalidPokemonDataException;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public enum Region {
    KANTO, JOHTO, HOENN, SINNOH, UNOVA, KALOS, ALOLA, GALAR, HISUI, PALDEA;

    public static Region fromString(String raw) {
        if (raw == null) {
            throw new InvalidPokemonDataException("region must be one of " + names());
        }
        return Arrays.stream(values())
                .filter(r -> r.name().equalsIgnoreCase(raw.strip()))
                .findFirst()
                .orElseThrow(() -> new InvalidPokemonDataException("region must be one of " + names()));
    }

    public static String names() {
        return Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", "));
    }

    public String display() {
        return name().charAt(0) + name().substring(1).toLowerCase(Locale.ROOT);
    }
}
