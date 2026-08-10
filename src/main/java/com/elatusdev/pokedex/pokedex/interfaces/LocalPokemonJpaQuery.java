package com.elatusdev.pokedex.pokedex.interfaces;

import com.elatusdev.pokedex.pokedex.domain.Region;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// One JPQL query with three optional predicates rather than a Specification tree: the
// filters are fixed and few, and ':x IS NULL OR ...' keeps the paged read and the count
// provably identical. Two hand-written queries that drift apart are how a total stops
// matching the page it describes.
//
// Every optional parameter is cast explicitly. Postgres cannot infer the type of a NULL
// bind, defaults it to bytea, and then fails with 'function lower(bytea) does not exist' —
// at runtime, on the no-filter path, which is the one most likely to reach production
// unexercised.
//
// The tag predicate correlates through p.tags because the association is unidirectional —
// PokemonTagDataModel carries no back-reference, so 't.pokemon = p' is not expressible.
// EXISTS rather than a join also keeps a record with two matching tags from being returned
// twice, which would inflate both the page and the count.
interface LocalPokemonJpaQuery extends JpaRepository<PokemonDataModel, Long> {

    @Query(
            """
            SELECT p FROM PokemonDataModel p
            WHERE (cast(:region as String) IS NULL OR p.region = :region)
              AND (cast(:name as String) IS NULL OR lower(p.name) LIKE lower(concat('%', cast(:name as String), '%')))
              AND (cast(:tag as String) IS NULL OR EXISTS (
                    SELECT 1 FROM p.tags t WHERE lower(t.label) = lower(cast(:tag as String))))
            ORDER BY p.id
            """)
    List<PokemonDataModel> findFilteredPage(
            @Param("region") Region region, @Param("tag") String tag, @Param("name") String name, Pageable pageable);

    @Query(
            """
            SELECT count(p) FROM PokemonDataModel p
            WHERE (cast(:region as String) IS NULL OR p.region = :region)
              AND (cast(:name as String) IS NULL OR lower(p.name) LIKE lower(concat('%', cast(:name as String), '%')))
              AND (cast(:tag as String) IS NULL OR EXISTS (
                    SELECT 1 FROM p.tags t WHERE lower(t.label) = lower(cast(:tag as String))))
            """)
    long countFiltered(@Param("region") Region region, @Param("tag") String tag, @Param("name") String name);
}
