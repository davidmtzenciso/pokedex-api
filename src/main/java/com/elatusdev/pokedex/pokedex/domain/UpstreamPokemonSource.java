package com.elatusdev.pokedex.pokedex.domain;

import com.elatusdev.pokedex.shared.domain.PokeApiId;
import com.elatusdev.pokedex.shared.domain.PokemonName;
import java.util.Optional;

// Declared by pokedex, satisfied by catalog. The dependency therefore points catalog ->
// pokedex, the same way the stale-fallback adapter does, and the two contexts never point at
// each other — which is the difference between an anti-corruption layer and a cycle.
public interface UpstreamPokemonSource {

    Optional<UpstreamPokemon> fetchById(PokeApiId pokeApiId);

    Optional<UpstreamPokemon> fetchByName(PokemonName name);
}
