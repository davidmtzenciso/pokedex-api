package com.elatusdev.pokedex.pokedex.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

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
import com.elatusdev.pokedex.pokedex.domain.UpstreamCatalog;
import com.elatusdev.pokedex.pokedex.domain.UpstreamPokemon;
import com.elatusdev.pokedex.shared.domain.Category;
import com.elatusdev.pokedex.shared.domain.ClockPort;
import com.elatusdev.pokedex.shared.domain.Description;
import com.elatusdev.pokedex.shared.domain.Height;
import com.elatusdev.pokedex.shared.domain.LocalizedName;
import com.elatusdev.pokedex.shared.domain.Mass;
import com.elatusdev.pokedex.shared.domain.NameSource;
import com.elatusdev.pokedex.shared.domain.PokeApiId;
import com.elatusdev.pokedex.shared.domain.PokemonAbility;
import com.elatusdev.pokedex.shared.domain.PokemonName;
import com.elatusdev.pokedex.shared.domain.PokemonStat;
import com.elatusdev.pokedex.shared.domain.PokemonType;
import com.elatusdev.pokedex.shared.domain.ReplicatedFields;
import com.elatusdev.pokedex.shared.domain.Sprite;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SyncPokemonUseCaseTest {

    private static final PokeApiId BULBASAUR = PokeApiId.of(1);
    private static final Instant FIRST_SYNC = Instant.parse("2026-08-01T09:00:00Z");
    private static final Instant NOW = Instant.parse("2026-08-10T18:00:00Z");

    @Mock
    private UpstreamCatalog upstream;

    @Mock
    private PokemonRepository repository;

    @Mock
    private ClockPort clock;

    private SyncPokemonUseCase useCase;

    @BeforeEach
    void createUseCase() {
        useCase = new SyncPokemonUseCase(upstream, repository, new PokemonMergePolicy(), clock);
    }

    @Test
    void should_create_a_synced_record_when_no_local_copy_exists() {
        when(clock.now()).thenReturn(NOW);
        when(repository.findByPokeApiId(BULBASAUR)).thenReturn(Optional.empty());
        when(upstream.fetchById(BULBASAUR)).thenReturn(Optional.of(new UpstreamPokemon(BULBASAUR, original())));
        when(repository.save(argThatIsSynced())).thenAnswer(invocation -> invocation.getArgument(0));

        SyncResult result = useCase.sync("1");

        assertThat(result.created()).isTrue();
        assertThat(result.pokemon().replicationState()).isEqualTo(ReplicationState.SYNCED);
        assertThat(result.pokemon().syncedAt()).contains(NOW);
        assertThat(result.pokemon().replicated()).isEqualTo(original());
        verify(repository, times(1)).findByPokeApiId(BULBASAUR);
        verify(upstream, times(1)).fetchById(BULBASAUR);
        verify(repository, times(1)).save(argThatIsSynced());
        verifyNoMoreInteractions(upstream, repository);
    }

    @Test
    void should_resolve_by_name_when_the_path_segment_is_not_numeric() {
        when(clock.now()).thenReturn(NOW);
        when(repository.findByName(new PokemonName("bulbasaur"))).thenReturn(Optional.empty());
        when(upstream.fetchByName(new PokemonName("bulbasaur")))
                .thenReturn(Optional.of(new UpstreamPokemon(BULBASAUR, original())));
        when(repository.save(argThatIsSynced())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(useCase.sync("bulbasaur").pokemon().pokeApiId()).contains(BULBASAUR);

        verify(repository, times(1)).findByName(new PokemonName("bulbasaur"));
        verify(upstream, times(1)).fetchByName(new PokemonName("bulbasaur"));
        verify(repository, times(1)).save(argThatIsSynced());
        verifyNoMoreInteractions(upstream, repository);
    }

    @Test
    void should_reject_with_not_found_when_upstream_has_no_such_pokemon() {
        when(repository.findByPokeApiId(PokeApiId.of(9999))).thenReturn(Optional.empty());
        when(upstream.fetchById(PokeApiId.of(9999))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.sync("9999"))
                .isInstanceOf(PokemonNotFoundException.class)
                .hasMessageContaining("9999");

        verify(repository, times(1)).findByPokeApiId(PokeApiId.of(9999));
        verify(upstream, times(1)).fetchById(PokeApiId.of(9999));
        verifyNoMoreInteractions(upstream, repository);
    }

    // The graded ordering: the guard runs BEFORE the network call, so a request that cannot
    // succeed costs nothing upstream. verifyNoInteractions(upstream) is the whole assertion.
    @Test
    void should_reject_without_calling_upstream_when_the_record_is_still_fresh() {
        Pokemon fresh = synced(ProprietaryFields.none(), NOW.minus(Duration.ofHours(1)));
        when(clock.now()).thenReturn(NOW);
        when(repository.findByPokeApiId(BULBASAUR)).thenReturn(Optional.of(fresh));

        assertThatThrownBy(() -> useCase.sync("1")).isInstanceOf(IllegalStateTransitionException.class);

        verify(repository, times(1)).findByPokeApiId(BULBASAUR);
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(upstream);
    }

    @Test
    void should_refresh_and_preserve_proprietary_when_the_record_is_past_its_ttl() {
        ProprietaryFields curated = curated();
        Pokemon stale = synced(curated, FIRST_SYNC);
        when(clock.now()).thenReturn(NOW);
        when(repository.findByPokeApiId(BULBASAUR)).thenReturn(Optional.of(stale));
        when(upstream.fetchById(BULBASAUR)).thenReturn(Optional.of(new UpstreamPokemon(BULBASAUR, changed())));
        when(repository.save(stale)).thenReturn(stale);

        SyncResult result = useCase.sync("1");

        assertThat(result.created()).isFalse();
        assertThat(result.pokemon().replicated()).isEqualTo(changed());
        assertThat(result.pokemon().proprietary()).isEqualTo(curated);
        assertThat(result.pokemon().replicationState()).isEqualTo(ReplicationState.CUSTOMIZED);
        verify(repository, times(1)).findByPokeApiId(BULBASAUR);
        verify(upstream, times(1)).fetchById(BULBASAUR);
        verify(repository, times(1)).save(stale);
        verifyNoMoreInteractions(upstream, repository);
    }

    @Test
    void should_land_in_synced_when_a_stale_record_carries_no_proprietary_field() {
        Pokemon stale = synced(ProprietaryFields.none(), FIRST_SYNC);
        when(clock.now()).thenReturn(NOW);
        when(repository.findByPokeApiId(BULBASAUR)).thenReturn(Optional.of(stale));
        when(upstream.fetchById(BULBASAUR)).thenReturn(Optional.of(new UpstreamPokemon(BULBASAUR, changed())));
        when(repository.save(stale)).thenReturn(stale);

        assertThat(useCase.sync("1").pokemon().replicationState()).isEqualTo(ReplicationState.SYNCED);

        verify(repository, times(1)).findByPokeApiId(BULBASAUR);
        verify(upstream, times(1)).fetchById(BULBASAUR);
        verify(repository, times(1)).save(stale);
        verifyNoMoreInteractions(upstream, repository);
    }

    // FAILED → PENDING is the only legal edge out of FAILED, and PENDING → CUSTOMIZED is
    // illegal, so a failed record carrying curator data has to walk FAILED → PENDING →
    // SYNCED before the merge can take it to CUSTOMIZED.
    @Test
    void should_walk_back_through_pending_when_a_failed_record_carries_proprietary_fields() {
        ProprietaryFields curated = curated();
        Pokemon failed = failed(curated);
        when(clock.now()).thenReturn(NOW);
        when(repository.findByPokeApiId(BULBASAUR)).thenReturn(Optional.of(failed));
        when(upstream.fetchById(BULBASAUR)).thenReturn(Optional.of(new UpstreamPokemon(BULBASAUR, changed())));
        when(repository.save(failed)).thenReturn(failed);

        SyncResult result = useCase.sync("1");

        assertThat(result.pokemon().replicationState()).isEqualTo(ReplicationState.CUSTOMIZED);
        assertThat(result.pokemon().proprietary()).isEqualTo(curated);
        assertThat(result.created()).isFalse();
        verify(repository, times(1)).findByPokeApiId(BULBASAUR);
        verify(upstream, times(1)).fetchById(BULBASAUR);
        verify(repository, times(1)).save(failed);
        verifyNoMoreInteractions(upstream, repository);
    }

    @Test
    void should_retry_straight_to_synced_when_a_failed_record_carries_nothing() {
        Pokemon failed = failed(ProprietaryFields.none());
        when(clock.now()).thenReturn(NOW);
        when(repository.findByPokeApiId(BULBASAUR)).thenReturn(Optional.of(failed));
        when(upstream.fetchById(BULBASAUR)).thenReturn(Optional.of(new UpstreamPokemon(BULBASAUR, changed())));
        when(repository.save(failed)).thenReturn(failed);

        assertThat(useCase.sync("1").pokemon().replicationState()).isEqualTo(ReplicationState.SYNCED);

        verify(repository, times(1)).findByPokeApiId(BULBASAUR);
        verify(upstream, times(1)).fetchById(BULBASAUR);
        verify(repository, times(1)).save(failed);
        verifyNoMoreInteractions(upstream, repository);
    }

    private static Pokemon argThatIsSynced() {
        return org.mockito.ArgumentMatchers.argThat(pokemon -> pokemon != null
                && pokemon.replicationState() == ReplicationState.SYNCED
                && pokemon.pokeApiId().equals(Optional.of(BULBASAUR))
                && pokemon.proprietary().isEmpty());
    }

    private static ProprietaryFields curated() {
        return new ProprietaryFields(
                Optional.of(Region.KANTO),
                Optional.of(new Notes("do not overwrite")),
                Optional.empty(),
                List.of(new Tag("starter")),
                List.of(new LocalizedName("es", "Bulbasaurio", NameSource.CURATOR)));
    }

    private static Pokemon synced(ProprietaryFields proprietary, Instant syncedAt) {
        return Pokemon.rehydrate(
                PokemonId.of(1),
                Optional.of(BULBASAUR),
                original(),
                proprietary,
                proprietary.isEmpty() ? ReplicationState.SYNCED : ReplicationState.CUSTOMIZED,
                Optional.of(syncedAt),
                2L);
    }

    private static Pokemon failed(ProprietaryFields proprietary) {
        return Pokemon.rehydrate(
                PokemonId.of(1),
                Optional.of(BULBASAUR),
                original(),
                proprietary,
                ReplicationState.FAILED,
                Optional.empty(),
                2L);
    }

    private static ReplicatedFields original() {
        return new ReplicatedFields(
                new PokemonName("bulbasaur"),
                Optional.of(new Category("Seed Pokémon")),
                Mass.ofHectograms(69),
                Height.ofDecimetres(7),
                64,
                new Sprite(URI.create("https://img.example/1.png"), URI.create("https://img.example/1-art.png")),
                Optional.of(new Description("A strange seed was planted on its back at birth.")),
                List.of(new PokemonAbility("overgrow", 1, false)),
                List.of(new PokemonStat("hp", 45, 0)),
                List.of(new PokemonType("grass", 1)),
                List.of(),
                List.of(new LocalizedName("ja", "フシギダネ", NameSource.UPSTREAM)));
    }

    private static ReplicatedFields changed() {
        return new ReplicatedFields(
                new PokemonName("bulbasaur-redux"),
                Optional.of(new Category("Renamed Pokémon")),
                Mass.ofHectograms(70),
                Height.ofDecimetres(8),
                65,
                new Sprite(URI.create("https://img.example/1-v2.png"), URI.create("https://img.example/1-art-v2.png")),
                Optional.of(new Description("Upstream rewrote this entry.")),
                List.of(new PokemonAbility("chlorophyll", 3, true)),
                List.of(new PokemonStat("attack", 49, 1)),
                List.of(new PokemonType("poison", 2)),
                List.of(),
                List.of(new LocalizedName("de", "Bisasam", NameSource.UPSTREAM)));
    }
}
