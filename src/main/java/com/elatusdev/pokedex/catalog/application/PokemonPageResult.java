package com.elatusdev.pokedex.catalog.application;

import com.elatusdev.pokedex.pokedex.domain.Pokemon;
import java.util.List;

// stale is a first-class part of the answer, not an error: an upstream outage with a local
// replica still answers the question, and the caller is told the data may have drifted
public record PokemonPageResult(List<Pokemon> rows, int page, int size, long totalElements, boolean stale) {

    public PokemonPageResult {
        rows = List.copyOf(rows);
    }

    public int totalPages() {
        return (int) Math.ceilDiv(totalElements, size);
    }
}
