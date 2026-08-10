package com.elatusdev.pokedex.catalog.domain;

import com.elatusdev.pokedex.shared.domain.PokeApiId;
import com.elatusdev.pokedex.shared.domain.PokemonName;
import java.util.Optional;

// the upstream catalogue. Implementations absorb the 1 + 2N fan-out (IA1, IA2) and return
// aggregates that are complete but not yet persisted, so callers never see a partial row
public interface PokemonCatalog {

    CatalogPage fetchPage(int page, int size);

    Optional<CatalogPokemon> fetchById(PokeApiId pokeApiId);

    Optional<CatalogPokemon> fetchByName(PokemonName name);
}
