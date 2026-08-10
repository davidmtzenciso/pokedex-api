package com.elatusdev.pokedex.pokedex.domain;

import com.elatusdev.pokedex.shared.domain.PokeApiId;
import com.elatusdev.pokedex.shared.domain.PokemonName;
import java.util.List;
import java.util.Optional;

public interface PokemonRepository {

    Optional<Pokemon> findById(PokemonId id);

    Optional<Pokemon> findByPokeApiId(PokeApiId pokeApiId);

    Optional<Pokemon> findByName(PokemonName name);

    // page metadata is derived by the use case; a domain port does not know about Pageable
    List<Pokemon> findPage(int page, int size);

    long count();

    boolean existsByPokeApiId(PokeApiId pokeApiId);

    Pokemon save(Pokemon pokemon);

    void delete(PokemonId id);
}
