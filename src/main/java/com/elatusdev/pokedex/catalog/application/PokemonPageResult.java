package com.elatusdev.pokedex.catalog.application;

import java.util.List;
import com.elatusdev.pokedex.catalog.domain.CatalogPokemon;

// stale is a first-class part of the answer, not an error: an upstream outage with a local
// replica still answers the question, and the caller is told the data may have drifted
public record PokemonPageResult(List<CatalogPokemon> rows, int page, int size, long totalElements, boolean stale) {

    public PokemonPageResult {
        rows = List.copyOf(rows);
    }

    public int totalPages() {
        return (int) Math.ceilDiv(totalElements, size);
    }
}
