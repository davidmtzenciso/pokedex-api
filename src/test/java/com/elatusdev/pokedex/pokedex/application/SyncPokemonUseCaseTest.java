package com.elatusdev.pokedex.pokedex.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.elatusdev.pokedex.catalog.domain.CatalogPokemon;
import com.elatusdev.pokedex.catalog.domain.PokemonCatalog;
import com.elatusdev.pokedex.pokedex.domain.DuplicatePokemonException;
import com.elatusdev.pokedex.pokedex.domain.Pokemon;
import com.elatusdev.pokedex.pokedex.domain.PokemonRepository;
import com.elatusdev.pokedex.pokedex.domain.ReplicationState;
import com.elatusdev.pokedex.shared.domain.Category;
import com.elatusdev.pokedex.shared.domain.ClockPort;
import com.elatusdev.pokedex.shared.domain.Height;
import com.elatusdev.pokedex.shared.domain.Mass;
import com.elatusdev.pokedex.shared.domain.PokeApiId;
import com.elatusdev.pokedex.shared.domain.PokemonName;
import com.elatusdev.pokedex.catalog.domain.PokemonNotFoundUpstreamException;
import com.elatusdev.pokedex.shared.domain.ReplicatedFields;
import com.elatusdev.pokedex.shared.domain.Sprite;
import com.elatusdev.pokedex.catalog.domain.UpstreamUnavailableException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SyncPokemonUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-08-10T09:00:00Z");

    private final PokemonCatalog catalog = mock(PokemonCatalog.class);
    private final PokemonRepository repository = mock(PokemonRepository.class);
    private final ClockPort clock = mock(ClockPort.class);
    private final SyncPokemonUseCase useCase = new SyncPokemonUseCase(catalog, repository, clock);

    private static ReplicatedFields bulbasaur() {
        return new ReplicatedFields(
                new PokemonName("bulbasaur"),
                Optional.of(new Category("Seed Pokemon")),
                Mass.ofHectograms(69),
                Height.ofDecimetres(7),
                64,
                Sprite.NONE,
                Optional.empty(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    private static CatalogPokemon upstream() {
        return CatalogPokemon.upstream(PokeApiId.of(1), bulbasaur());
    }

    @Test
    void should_replicate_a_new_pokemon_as_synced_when_it_is_not_stored_yet() {
        when(clock.now()).thenReturn(NOW);
        when(catalog.fetchById(PokeApiId.of(1))).thenReturn(Optional.of(upstream()));
        when(repository.existsByPokeApiId(PokeApiId.of(1))).thenReturn(false);
        when(repository.save(org.mockito.ArgumentMatchers.argThat(pokemon -> pokemon != null
                        && pokemon.replicationState() == ReplicationState.SYNCED
                        && pokemon.pokeApiId().equals(Optional.of(PokeApiId.of(1)))
                        && pokemon.syncedAt().equals(Optional.of(NOW))
                        && pokemon.replicated().equals(bulbasaur()))))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Pokemon replicated = useCase.sync("1");

        assertThat(replicated.replicationState()).isEqualTo(ReplicationState.SYNCED);
        assertThat(replicated.syncedAt()).contains(NOW);
        assertThat(replicated.replicated()).isEqualTo(bulbasaur());
        verify(repository, times(1)).existsByPokeApiId(PokeApiId.of(1));
        verify(repository, times(1)).save(org.mockito.ArgumentMatchers.argThat(pokemon -> pokemon != null));
        verifyNoMoreInteractions(repository);
    }

    @Test
    void should_resolve_a_name_reference_against_the_catalogue() {
        when(clock.now()).thenReturn(NOW);
        when(catalog.fetchByName(new PokemonName("bulbasaur"))).thenReturn(Optional.of(upstream()));
        when(repository.existsByPokeApiId(PokeApiId.of(1))).thenReturn(false);
        when(repository.save(org.mockito.ArgumentMatchers.argThat(pokemon -> pokemon != null)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        useCase.sync("bulbasaur");

        verify(catalog, times(1)).fetchByName(new PokemonName("bulbasaur"));
        verifyNoMoreInteractions(catalog);
    }

    @Test
    void should_raise_not_found_upstream_when_the_catalogue_has_no_such_pokemon() {
        when(catalog.fetchByName(new PokemonName("missingno"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.sync("missingno"))
                .isInstanceOf(PokemonNotFoundUpstreamException.class)
                .hasMessageContaining("missingno");

        verify(repository, never()).save(org.mockito.ArgumentMatchers.argThat(pokemon -> true));
    }

    // 409 rather than a silent second copy: this endpoint replicates, and refreshing an
    // existing record is re-sync's job with its own state guard
    @Test
    void should_reject_a_pokemon_that_is_already_replicated() {
        when(catalog.fetchById(PokeApiId.of(1))).thenReturn(Optional.of(upstream()));
        when(repository.existsByPokeApiId(PokeApiId.of(1))).thenReturn(true);

        assertThatThrownBy(() -> useCase.sync("1"))
                .isInstanceOf(DuplicatePokemonException.class)
                .hasMessageContaining("1");

        verify(repository, times(1)).existsByPokeApiId(PokeApiId.of(1));
        verifyNoMoreInteractions(repository);
    }

    // nothing is persisted for a first sync that never landed: there is no row to mark
    // FAILED, and inventing one would claim an upstream id this service never replicated
    @Test
    void should_persist_nothing_when_the_first_sync_cannot_reach_upstream() {
        when(catalog.fetchById(PokeApiId.of(1)))
                .thenThrow(new UpstreamUnavailableException("pokeapi down", null));

        assertThatThrownBy(() -> useCase.sync("1")).isInstanceOf(UpstreamUnavailableException.class);

        verifyNoInteractions(repository);
    }

    @Test
    void should_reject_a_blank_reference() {
        assertThatThrownBy(() -> useCase.sync("  "))
                .isInstanceOf(com.elatusdev.pokedex.shared.domain.InvalidPokemonDataException.class);

        verifyNoInteractions(catalog);
        verifyNoInteractions(repository);
    }
}
