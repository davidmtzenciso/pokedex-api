package com.elatusdev.pokedex.pokedex.domain;

import java.util.Objects;
import java.util.Optional;

// The three filters compose: each present one narrows the result further, and an empty
// filter matches everything. Absent is Optional rather than null so that "no region
// filter" and "region filter that matched nothing" cannot be confused at the call site.
public record LocalPokemonFilter(Optional<Region> region, Optional<Tag> tag, Optional<String> nameContains) {

    public LocalPokemonFilter {
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(nameContains, "nameContains");
        nameContains = nameContains.map(String::strip).filter(value -> !value.isEmpty());
    }

    public static LocalPokemonFilter none() {
        return new LocalPokemonFilter(Optional.empty(), Optional.empty(), Optional.empty());
    }

    public boolean isEmpty() {
        return region.isEmpty() && tag.isEmpty() && nameContains.isEmpty();
    }
}
