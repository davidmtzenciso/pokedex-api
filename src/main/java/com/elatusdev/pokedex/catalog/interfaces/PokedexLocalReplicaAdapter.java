package com.elatusdev.pokedex.catalog.interfaces;

import com.elatusdev.pokedex.catalog.domain.CatalogPokemon;
import com.elatusdev.pokedex.catalog.domain.LocalReplica;
import com.elatusdev.pokedex.pokedex.domain.Pokemon;
import com.elatusdev.pokedex.pokedex.domain.PokemonRepository;
import com.elatusdev.pokedex.shared.domain.PokeApiId;
import com.elatusdev.pokedex.shared.domain.PokemonName;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

// The anti-corruption layer between the two contexts, and deliberately the only file in
// catalog that imports anything from pokedex. It takes the curated aggregate and hands
// back the replicated view, dropping region, notes, tags, replication state and the local
// id on the way through.
//
// Before this existed, catalog imported Pokemon in ten files and returned the aggregate
// straight to the wire.
@Component
public class PokedexLocalReplicaAdapter implements LocalReplica {

    private final PokemonRepository pokemon;

    public PokedexLocalReplicaAdapter(PokemonRepository pokemon) {
        this.pokemon = pokemon;
    }

    @Override
    public Optional<CatalogPokemon> findByPokeApiId(PokeApiId pokeApiId) {
        return pokemon.findByPokeApiId(pokeApiId).map(PokedexLocalReplicaAdapter::toCatalogView);
    }

    @Override
    public Optional<CatalogPokemon> findByName(PokemonName name) {
        return pokemon.findByName(name).map(PokedexLocalReplicaAdapter::toCatalogView);
    }

    @Override
    public List<CatalogPokemon> findPage(int page, int size) {
        return pokemon.findPage(page, size).stream()
                .map(PokedexLocalReplicaAdapter::toCatalogView)
                .toList();
    }

    @Override
    public long count() {
        return pokemon.count();
    }

    private static CatalogPokemon toCatalogView(Pokemon aggregate) {
        return new CatalogPokemon(aggregate.pokeApiId(), aggregate.replicated());
    }
}
