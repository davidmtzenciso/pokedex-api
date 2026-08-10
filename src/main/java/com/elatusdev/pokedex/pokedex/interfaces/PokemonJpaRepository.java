package com.elatusdev.pokedex.pokedex.interfaces;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.elatusdev.pokedex.shared.domain.PokemonName;

interface PokemonJpaRepository extends JpaRepository<PokemonDataModel, Long> {

    Optional<PokemonDataModel> findByPokeApiId(Integer pokeApiId);

    boolean existsByPokeApiId(Integer pokeApiId);

    // PokemonName compares case-insensitively, so the lookup must too. Written out rather
    // than derived because Spring Data's IgnoringCase emits upper() and the index is on
    // lower(name) — a derived query would silently seq-scan.
    //
    // A list, not an Optional: only poke_api_id is unique (I1), so two hand-created drafts
    // may share a name, and a single-result query would answer that with a 500.
    @Query("SELECT p FROM PokemonDataModel p WHERE lower(p.name) = lower(:name) ORDER BY p.id")
    List<PokemonDataModel> findByNameIgnoringCase(@Param("name") String name);

    // A List, not a Page. Page would issue a SELECT count(*) alongside every page read, and
    // the port asks for the total separately through count() — so the paged read would be
    // paying for a number nobody requested on every call.
    @Query("SELECT p FROM PokemonDataModel p ORDER BY p.id")
    List<PokemonDataModel> findPage(Pageable pageable);
}
