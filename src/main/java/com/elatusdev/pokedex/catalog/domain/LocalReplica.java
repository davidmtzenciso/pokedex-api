package com.elatusdev.pokedex.catalog.domain;

import com.elatusdev.pokedex.shared.domain.PokeApiId;
import com.elatusdev.pokedex.shared.domain.PokemonName;
import java.util.List;
import java.util.Optional;

// The catalogue's own view of the local store, declared in the catalogue's own terms. It
// exists so that an upstream outage can still be answered from what was replicated
// earlier (stale = true) without this context depending on the pokedex aggregate.
//
// The adapter that satisfies it is the single place the two contexts touch.
public interface LocalReplica {

    Optional<CatalogPokemon> findByPokeApiId(PokeApiId pokeApiId);

    Optional<CatalogPokemon> findByName(PokemonName name);

    List<CatalogPokemon> findPage(int page, int size);

    long count();
}
