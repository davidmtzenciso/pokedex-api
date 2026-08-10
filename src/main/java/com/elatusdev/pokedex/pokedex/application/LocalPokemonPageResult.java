package com.elatusdev.pokedex.pokedex.application;

import com.elatusdev.pokedex.pokedex.domain.Pokemon;
import java.util.List;
import java.util.Objects;

public record LocalPokemonPageResult(List<Pokemon> rows, int page, int size, long totalCount) {

    public LocalPokemonPageResult {
        rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
    }
}
