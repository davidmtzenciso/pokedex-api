package com.elatusdev.pokedex.catalog.domain;

import java.util.List;

// Rows and total together, because upstream returns them together. Splitting them across
// two port methods costs a second listing call and turns 1 + 2N into 2 + 2N.
public record CatalogPage(List<CatalogPokemon> rows, int totalCount) {

    public CatalogPage {
        rows = List.copyOf(rows);
    }
}
