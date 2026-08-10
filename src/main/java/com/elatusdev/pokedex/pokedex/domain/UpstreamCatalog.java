package com.elatusdev.pokedex.pokedex.domain;

import com.elatusdev.pokedex.shared.domain.PokeApiId;
import com.elatusdev.pokedex.shared.domain.PokemonName;
import java.util.Optional;

// What replication needs from upstream, said in pokedex's own words.
//
// This duplicates catalog's PokemonCatalog in shape and that is not an oversight. catalog
// already depends on pokedex through the LocalReplica bridge, so naming PokemonCatalog here
// would close a context cycle and fail CY1 — proven, not assumed. The port therefore belongs
// to its consumer and is satisfied from the other side by
// catalog/interfaces/PokedexUpstreamCatalogAdapter, which keeps every crossing pointing
// catalog → pokedex.
//
// It names only shared types, so it carries no knowledge of how the catalogue fetches,
// caches, or fans out — which is the whole point of the boundary.
public interface UpstreamCatalog {

    Optional<UpstreamPokemon> fetchById(PokeApiId pokeApiId);

    Optional<UpstreamPokemon> fetchByName(PokemonName name);
}
