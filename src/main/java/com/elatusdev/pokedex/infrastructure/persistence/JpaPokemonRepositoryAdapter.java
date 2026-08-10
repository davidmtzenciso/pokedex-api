package com.elatusdev.pokedex.infrastructure.persistence;

import com.elatusdev.pokedex.domain.model.Pokemon;
import com.elatusdev.pokedex.domain.port.PokemonRepository;
import com.elatusdev.pokedex.domain.vo.PokeApiId;
import com.elatusdev.pokedex.domain.vo.PokemonId;
import com.elatusdev.pokedex.domain.vo.PokemonName;
import com.elatusdev.pokedex.infrastructure.persistence.mapper.PokemonPersistenceMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Domain types in, domain types out. Nothing a caller receives from here is a JPA proxy.
//
// The read methods are transactional with the default REQUIRED propagation, so inside a use
// case they join its transaction and change no boundary. Outside one they open a short read
// transaction, which is what lets the mapper walk the lazy child collections at all: without
// it every returned aggregate would throw LazyInitializationException the first time anyone
// asked it for its abilities.
@Component
@Transactional(readOnly = true)
public class JpaPokemonRepositoryAdapter implements PokemonRepository {

    private final PokemonJpaRepository jpaRepository;
    private final PokemonPersistenceMapper mapper;

    JpaPokemonRepositoryAdapter(PokemonJpaRepository jpaRepository, PokemonPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<Pokemon> findById(PokemonId id) {
        return jpaRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<Pokemon> findByPokeApiId(PokeApiId pokeApiId) {
        return jpaRepository.findByPokeApiId(pokeApiId.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<Pokemon> findByName(PokemonName name) {
        return jpaRepository.findByNameIgnoringCase(name.value()).stream()
                .findFirst()
                .map(mapper::toDomain);
    }

    @Override
    public List<Pokemon> findPage(int page, int size) {
        return jpaRepository.findPage(PageRequest.of(page, size)).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public long count() {
        return jpaRepository.count();
    }

    @Override
    public boolean existsByPokeApiId(PokeApiId pokeApiId) {
        return jpaRepository.existsByPokeApiId(pokeApiId.value());
    }

    // saveAndFlush, not save: the write has to reach the database inside this call so an
    // optimistic-lock conflict is thrown here and can be mapped to 412. Deferred to commit it
    // would surface after the use case has already returned a success.
    @Override
    @Transactional
    public Pokemon save(Pokemon pokemon) {
        return mapper.toDomain(jpaRepository.saveAndFlush(mapper.toDataModel(pokemon)));
    }

    @Override
    @Transactional
    public void delete(PokemonId id) {
        jpaRepository.deleteById(id.value());
    }
}
