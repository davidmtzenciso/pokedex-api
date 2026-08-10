package com.elatusdev.pokedex.shared.domain;

import java.util.Locale;
import java.util.Objects;

public record LocalizedName(String locale, String value, NameSource source) {

    public static final int MAX_LENGTH = 120;

    public LocalizedName {
        Objects.requireNonNull(locale, "locale");
        Objects.requireNonNull(value, "localized name");
        Objects.requireNonNull(source, "source");
        locale = locale.strip().toLowerCase(Locale.ROOT);
        value = value.strip();
        requireLocale(locale);
        requireValue(value);
    }

    private static void requireLocale(String locale) {
        if (locale.isEmpty()) {
            throw new InvalidPokemonDataException("locale must be non-blank");
        }
    }

    private static void requireValue(String value) {
        if (value.isEmpty() || value.length() > MAX_LENGTH) {
            throw new InvalidPokemonDataException("localized name must be 1.." + MAX_LENGTH + " characters");
        }
    }
}
