// Copyright (c) 2026 ElatusDev
package com.elatusdev.pokedex.domain.port;

import com.elatusdev.pokedex.domain.model.Pokemon;
import com.elatusdev.pokedex.domain.vo.PokeApiId;
import com.elatusdev.pokedex.domain.vo.PokemonName;
import java.util.List;
import java.util.Optional;

// the upstream catalogue. Implementations absorb the 1 + 2N fan-out (IA1, IA2) and return
// aggregates that are complete but not yet persisted, so callers never see a partial row
public interface PokemonCatalog {

    List<Pokemon> fetchPage(int page, int size);

    int totalCount();

    Optional<Pokemon> fetchById(PokeApiId pokeApiId);

    Optional<Pokemon> fetchByName(PokemonName name);
}
