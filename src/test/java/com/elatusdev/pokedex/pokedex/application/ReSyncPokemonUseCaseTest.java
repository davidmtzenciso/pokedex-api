package com.elatusdev.pokedex.pokedex.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.elatusdev.pokedex.pokedex.domain.UpstreamPokemon;
import com.elatusdev.pokedex.pokedex.domain.UpstreamPokemonSource;
import com.elatusdev.pokedex.shared.domain.PokemonNotFoundUpstreamException;
import com.elatusdev.pokedex.shared.domain.UpstreamUnavailableException;
import com.elatusdev.pokedex.identity.domain.UserId;
import com.elatusdev.pokedex.pokedex.domain.IllegalStateTransitionException;
import com.elatusdev.pokedex.pokedex.domain.Notes;
import com.elatusdev.pokedex.pokedex.domain.Pokemon;
import com.elatusdev.pokedex.pokedex.domain.PokemonId;
import com.elatusdev.pokedex.pokedex.domain.PokemonMergePolicy;
import com.elatusdev.pokedex.pokedex.domain.PokemonNotFoundException;
import com.elatusdev.pokedex.pokedex.domain.PokemonRepository;
import com.elatusdev.pokedex.pokedex.domain.ProprietaryFields;
import com.elatusdev.pokedex.pokedex.domain.Region;
import com.elatusdev.pokedex.pokedex.domain.ReplicationState;
import com.elatusdev.pokedex.pokedex.domain.Tag;
import com.elatusdev.pokedex.shared.domain.Category;
import com.elatusdev.pokedex.shared.domain.ClockPort;
import com.elatusdev.pokedex.shared.domain.Height;
import com.elatusdev.pokedex.shared.domain.LocalizedName;
import com.elatusdev.pokedex.shared.domain.Mass;
import com.elatusdev.pokedex.shared.domain.NameSource;
import com.elatusdev.pokedex.shared.domain.PokeApiId;
import com.elatusdev.pokedex.shared.domain.PokemonName;
import com.elatusdev.pokedex.shared.domain.ReplicatedFields;
import com.elatusdev.pokedex.shared.domain.Sprite;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReSyncPokemonUseCaseTest {

    private static final Instant SYNCED_AT = Instant.parse("2026-08-01T09:00:00Z");
    private static final Instant NOW = Instant.parse("2026-08-10T09:00:00Z");

    private final UpstreamPokemonSource catalog = mock(UpstreamPokemonSource.class);
    private final PokemonRepository repository = mock(PokemonRepository.class);
    private final ClockPort clock = mock(ClockPort.class);
    private final ReSyncPokemonUseCase useCase =
            new ReSyncPokemonUseCase(catalog, repository, new PokemonMergePolicy(), clock);

    private static ProprietaryFields curated() {
        return new ProprietaryFields(
                Optional.of(Region.KANTO),
                Optional.of(new Notes("verify the sprite")),
                Optional.of(UserId.of(7)),
                List.of(new Tag("starter")),
                List.of(new LocalizedName("es", "Bulbasaur", NameSource.CURATOR)));
    }

    private static ReplicatedFields stored() {
        return replicated("bulbasaur", 69);
    }

    private static ReplicatedFields replicated(String name, int hectograms) {
        return new ReplicatedFields(
                new PokemonName(name),
                Optional.of(new Category("Seed Pokemon")),
                Mass.ofHectograms(hectograms),
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

    private static Pokemon record(ReplicationState state, ProprietaryFields proprietary) {
        return Pokemon.rehydrate(
                PokemonId.of(1),
                Optional.of(PokeApiId.of(1)),
                stored(),
                proprietary,
                state,
                Optional.of(SYNCED_AT),
                3L);
    }

    private void upstreamReturns(String name, int hectograms) {
        when(catalog.fetchById(PokeApiId.of(1)))
                .thenReturn(Optional.of(new UpstreamPokemon(PokeApiId.of(1), replicated(name, hectograms))));
    }

    private void saveEchoes() {
        when(repository.save(argThat(pokemon -> pokemon != null)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void should_land_on_synced_when_a_stale_record_carries_no_curator_data() {
        when(clock.now()).thenReturn(NOW);
        when(repository.findById(PokemonId.of(1)))
                .thenReturn(Optional.of(record(ReplicationState.STALE, ProprietaryFields.none())));
        upstreamReturns("ivysaur", 130);
        saveEchoes();

        Pokemon refreshed = useCase.reSync(PokemonId.of(1));

        assertThat(refreshed.replicationState()).isEqualTo(ReplicationState.SYNCED);
        assertThat(refreshed.replicated().name()).isEqualTo(new PokemonName("ivysaur"));
        assertThat(refreshed.syncedAt()).contains(NOW);
    }

    @Test
    void should_land_on_customized_when_a_stale_record_carries_curator_data() {
        when(clock.now()).thenReturn(NOW);
        when(repository.findById(PokemonId.of(1)))
                .thenReturn(Optional.of(record(ReplicationState.STALE, curated())));
        upstreamReturns("ivysaur", 130);
        saveEchoes();

        assertThat(useCase.reSync(PokemonId.of(1)).replicationState())
                .isEqualTo(ReplicationState.CUSTOMIZED);
    }

    // AC5 at the use-case level. PokemonMergePolicyTest proves the property over all 32
    // combinations; this proves the use case actually routes through the policy.
    @Test
    void should_leave_every_proprietary_field_byte_identical_after_a_refresh() {
        when(clock.now()).thenReturn(NOW);
        when(repository.findById(PokemonId.of(1)))
                .thenReturn(Optional.of(record(ReplicationState.STALE, curated())));
        upstreamReturns("ivysaur", 130);
        saveEchoes();

        Pokemon refreshed = useCase.reSync(PokemonId.of(1));

        assertThat(refreshed.proprietary()).isEqualTo(curated());
        assertThat(refreshed.replicated()).isEqualTo(replicated("ivysaur", 130));
    }

    // the guard is the point: an upstream request costs a rate-limited call, so a record in
    // the wrong state must be rejected before one is spent
    @Test
    void should_reject_a_record_that_is_not_stale_or_failed_without_calling_upstream() {
        when(repository.findById(PokemonId.of(1)))
                .thenReturn(Optional.of(record(ReplicationState.SYNCED, ProprietaryFields.none())));

        assertThatThrownBy(() -> useCase.reSync(PokemonId.of(1)))
                .isInstanceOf(IllegalStateTransitionException.class);

        verifyNoInteractions(catalog);
        verify(repository, times(1)).findById(PokemonId.of(1));
    }

    // FAILED's only legal successor is PENDING, and PENDING's is SYNCED — so a customised
    // record that failed has to be routed back through both rather than jumping to CUSTOMIZED
    @Test
    void should_retry_a_failed_record_through_pending_and_keep_its_curator_data() {
        when(clock.now()).thenReturn(NOW);
        when(repository.findById(PokemonId.of(1)))
                .thenReturn(Optional.of(record(ReplicationState.FAILED, curated())));
        upstreamReturns("ivysaur", 130);
        saveEchoes();

        Pokemon refreshed = useCase.reSync(PokemonId.of(1));

        assertThat(refreshed.replicationState()).isEqualTo(ReplicationState.CUSTOMIZED);
        assertThat(refreshed.proprietary()).isEqualTo(curated());
    }

    @Test
    void should_mark_the_record_failed_when_upstream_is_unavailable() {
        when(clock.now()).thenReturn(NOW);
        when(repository.findById(PokemonId.of(1)))
                .thenReturn(Optional.of(record(ReplicationState.STALE, ProprietaryFields.none())));
        when(catalog.fetchById(PokeApiId.of(1))).thenThrow(new UpstreamUnavailableException("pokeapi down", null));
        saveEchoes();

        assertThatThrownBy(() -> useCase.reSync(PokemonId.of(1)))
                .isInstanceOf(UpstreamUnavailableException.class);

        verify(repository, times(1))
                .save(argThat(pokemon -> pokemon.replicationState() == ReplicationState.FAILED));
    }

    // FAILED -> FAILED is not on the diagram, so a record that is already failed must be
    // left alone rather than transitioned again; the outage is still what propagates
    @Test
    void should_not_transition_a_record_that_is_already_failed_when_upstream_is_unavailable() {
        when(repository.findById(PokemonId.of(1)))
                .thenReturn(Optional.of(record(ReplicationState.FAILED, ProprietaryFields.none())));
        when(catalog.fetchById(PokeApiId.of(1))).thenThrow(new UpstreamUnavailableException("pokeapi down", null));

        assertThatThrownBy(() -> useCase.reSync(PokemonId.of(1)))
                .isInstanceOf(UpstreamUnavailableException.class);

        verify(repository, times(1)).findById(PokemonId.of(1));
        org.mockito.Mockito.verifyNoMoreInteractions(repository);
    }

    @Test
    void should_raise_not_found_when_no_such_record_is_stored() {
        when(repository.findById(PokemonId.of(9999))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.reSync(PokemonId.of(9999)))
                .isInstanceOf(PokemonNotFoundException.class);

        verifyNoInteractions(catalog);
    }

    @Test
    void should_raise_not_found_upstream_when_the_catalogue_lost_the_pokemon() {
        when(repository.findById(PokemonId.of(1)))
                .thenReturn(Optional.of(record(ReplicationState.STALE, ProprietaryFields.none())));
        when(catalog.fetchById(PokeApiId.of(1))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.reSync(PokemonId.of(1)))
                .isInstanceOf(PokemonNotFoundUpstreamException.class);
    }
}
