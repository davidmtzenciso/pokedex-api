package com.elatusdev.pokedex.catalog.infrastructure;

import com.elatusdev.pokedex.pokedex.domain.Pokemon;
import com.elatusdev.pokedex.pokedex.domain.PokemonRepository;
import com.elatusdev.pokedex.shared.domain.PokeApiId;
import com.elatusdev.pokedex.pokedex.domain.PokemonId;
import com.elatusdev.pokedex.shared.domain.PokemonName;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.elatusdev.pokedex.pokedex.interfaces.JpaPokemonRepositoryAdapter;
import com.elatusdev.pokedex.catalog.application.ListPokemonUseCase;

// TEMPORARY, AND SELF-REMOVING. The real adapter is WU-US03-A; until it exists the
// application context cannot satisfy ListPokemonUseCase's PokemonRepository dependency and
// will not start at all.
//
// Reads report "nothing stored", which is exactly right: with no replica there is nothing
// to fall back to, so an upstream outage propagates (AC-US01-6) instead of being masked.
// Writes THROW rather than silently succeeding — a no-op save would let another stream
// believe it had persisted something.
//
// @ConditionalOnMissingBean means this disappears the moment JpaPokemonRepositoryAdapter
// is on the context. Delete this package when WU-US03-A merges.
@Configuration
public class UnavailableLocalReplica {

    @Bean
    @ConditionalOnMissingBean(PokemonRepository.class)
    public PokemonRepository absentLocalReplica() {
        return new AbsentReplica();
    }

    static final class AbsentReplica implements PokemonRepository {

        @Override
        public Optional<Pokemon> findById(PokemonId id) {
            return Optional.empty();
        }

        @Override
        public Optional<Pokemon> findByPokeApiId(PokeApiId pokeApiId) {
            return Optional.empty();
        }

        @Override
        public Optional<Pokemon> findByName(PokemonName name) {
            return Optional.empty();
        }

        @Override
        public List<Pokemon> findPage(int page, int size) {
            return List.of();
        }

        @Override
        public long count() {
            return 0L;
        }

        @Override
        public boolean existsByPokeApiId(PokeApiId pokeApiId) {
            return false;
        }

        @Override
        public Pokemon save(Pokemon pokemon) {
            throw new UnsupportedOperationException("no local replica is configured; WU-US03-A provides it");
        }

        @Override
        public void delete(PokemonId id) {
            throw new UnsupportedOperationException("no local replica is configured; WU-US03-A provides it");
        }
    }
}
