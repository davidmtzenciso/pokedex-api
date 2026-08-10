package com.elatusdev.pokedex.catalog.interfaces;

import com.elatusdev.pokedex.catalog.domain.CatalogPokemon;
import com.elatusdev.pokedex.catalog.domain.PokemonCatalog;
import com.elatusdev.pokedex.pokedex.domain.UpstreamPokemon;
import com.elatusdev.pokedex.pokedex.domain.UpstreamPokemonSource;
import com.elatusdev.pokedex.shared.domain.PokeApiId;
import com.elatusdev.pokedex.shared.domain.PokemonName;
import java.util.Optional;
import org.springframework.stereotype.Component;

// The second half of the anti-corruption layer, and the mirror image of
// PokedexLocalReplicaAdapter: replication states what it needs as UpstreamPokemon, and this
// maps the catalogue's own read model onto it.
//
// It lives in catalog rather than pokedex deliberately. Both bridges must point the same way
// — catalog -> pokedex — or the contexts depend on each other and CY1 goes red. A record the
// catalogue cannot identify upstream is dropped rather than forwarded: replication has no
// use for a row with no upstream identity.
@Component
public class PokedexUpstreamSourceAdapter implements UpstreamPokemonSource {

    private final PokemonCatalog catalog;

    PokedexUpstreamSourceAdapter(PokemonCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public Optional<UpstreamPokemon> fetchById(PokeApiId pokeApiId) {
        return catalog.fetchById(pokeApiId).flatMap(PokedexUpstreamSourceAdapter::toUpstream);
    }

    @Override
    public Optional<UpstreamPokemon> fetchByName(PokemonName name) {
        return catalog.fetchByName(name).flatMap(PokedexUpstreamSourceAdapter::toUpstream);
    }

    private static Optional<UpstreamPokemon> toUpstream(CatalogPokemon catalogPokemon) {
        return catalogPokemon.pokeApiId().map(id -> new UpstreamPokemon(id, catalogPokemon.replicated()));
    }
}
