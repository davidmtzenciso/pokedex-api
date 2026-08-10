package com.elatusdev.pokedex.pokedex.domain.port;

import com.elatusdev.pokedex.pokedex.domain.model.Pokemon;
import com.elatusdev.pokedex.shared.domain.vo.PokeApiId;
import com.elatusdev.pokedex.shared.domain.vo.PokemonName;
import java.util.Optional;

// the upstream catalogue. Implementations absorb the 1 + 2N fan-out (IA1, IA2) and return
// aggregates that are complete but not yet persisted, so callers never see a partial row
public interface PokemonCatalog {

    CatalogPage fetchPage(int page, int size);

    Optional<Pokemon> fetchById(PokeApiId pokeApiId);

    Optional<Pokemon> fetchByName(PokemonName name);
}
