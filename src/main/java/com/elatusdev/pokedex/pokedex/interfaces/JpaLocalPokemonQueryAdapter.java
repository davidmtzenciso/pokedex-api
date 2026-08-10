package com.elatusdev.pokedex.pokedex.interfaces;

import com.elatusdev.pokedex.pokedex.domain.LocalPokemonFilter;
import com.elatusdev.pokedex.pokedex.domain.LocalPokemonQuery;
import com.elatusdev.pokedex.pokedex.domain.Pokemon;
import com.elatusdev.pokedex.pokedex.domain.Region;
import com.elatusdev.pokedex.pokedex.domain.Tag;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Domain types in, domain types out; no JPA proxy escapes. Read-only and transactional for
// the same reason as JpaPokemonRepositoryAdapter — without a transaction the mapper cannot
// walk the lazy child collections.
@Component
@Transactional(readOnly = true)
public class JpaLocalPokemonQueryAdapter implements LocalPokemonQuery {

    private final LocalPokemonJpaQuery jpaQuery;
    private final PokemonPersistenceMapper mapper;

    JpaLocalPokemonQueryAdapter(LocalPokemonJpaQuery jpaQuery, PokemonPersistenceMapper mapper) {
        this.jpaQuery = jpaQuery;
        this.mapper = mapper;
    }

    @Override
    public List<Pokemon> findPage(LocalPokemonFilter filter, int page, int size) {
        return jpaQuery
                .findFilteredPage(region(filter), tag(filter), name(filter), PageRequest.of(page, size))
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public long count(LocalPokemonFilter filter) {
        return jpaQuery.countFiltered(region(filter), tag(filter), name(filter));
    }

    // null is the "no filter" signal the JPQL tests with IS NULL; it never leaves this class
    private static Region region(LocalPokemonFilter filter) {
        return filter.region().orElse(null);
    }

    private static String tag(LocalPokemonFilter filter) {
        return filter.tag().map(Tag::label).orElse(null);
    }

    private static String name(LocalPokemonFilter filter) {
        return filter.nameContains().orElse(null);
    }
}
