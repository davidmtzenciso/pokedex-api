package com.elatusdev.pokedex.catalog.interfaces;

import com.elatusdev.pokedex.catalog.domain.CatalogPokemon;
import com.elatusdev.pokedex.catalog.domain.PokemonCatalog;
import com.elatusdev.pokedex.catalog.domain.UpstreamTimeoutException;
import com.elatusdev.pokedex.catalog.domain.UpstreamUnavailableException;
import com.elatusdev.pokedex.pokedex.domain.UpstreamCatalog;
import com.elatusdev.pokedex.pokedex.domain.UpstreamPokemon;
import com.elatusdev.pokedex.pokedex.domain.UpstreamReplicationFailedException;
import com.elatusdev.pokedex.shared.domain.PokeApiId;
import com.elatusdev.pokedex.shared.domain.PokemonName;
import java.util.Optional;
import org.springframework.stereotype.Component;

// The second of the two adapters BC5 names, and the reason it lives in catalog rather than
// beside the port it implements: catalog already depends on pokedex through
// PokedexLocalReplicaAdapter, so an adapter placed in pokedex/interfaces would point the
// other way and close a context cycle. CY1 fails on it — that was measured, not predicted.
//
// The port belongs to its consumer and the implementation to the side that can afford the
// dependency. It maps rather than forwards: UpstreamPokemon requires the upstream id that
// CatalogPokemon leaves optional, so a record without one is not replicable and is dropped
// here rather than becoming a null further in.
@Component
public class PokedexUpstreamCatalogAdapter implements UpstreamCatalog {

    private final PokemonCatalog catalog;

    PokedexUpstreamCatalogAdapter(PokemonCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public Optional<UpstreamPokemon> fetchById(PokeApiId pokeApiId) {
        try {
            return catalog.fetchById(pokeApiId).flatMap(PokedexUpstreamCatalogAdapter::replicable);
        } catch (UpstreamUnavailableException | UpstreamTimeoutException failure) {
            throw new UpstreamReplicationFailedException(String.valueOf(pokeApiId.value()), failure);
        }
    }

    @Override
    public Optional<UpstreamPokemon> fetchByName(PokemonName name) {
        try {
            return catalog.fetchByName(name).flatMap(PokedexUpstreamCatalogAdapter::replicable);
        } catch (UpstreamUnavailableException | UpstreamTimeoutException failure) {
            throw new UpstreamReplicationFailedException(name.value(), failure);
        }
    }

    private static Optional<UpstreamPokemon> replicable(CatalogPokemon fetched) {
        return fetched.pokeApiId().map(id -> new UpstreamPokemon(id, fetched.replicated()));
    }
}
