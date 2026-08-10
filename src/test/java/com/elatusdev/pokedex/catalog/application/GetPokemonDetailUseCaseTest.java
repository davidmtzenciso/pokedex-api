package com.elatusdev.pokedex.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.elatusdev.pokedex.shared.domain.InvalidPokemonDataException;
import com.elatusdev.pokedex.catalog.domain.PokemonNotFoundUpstreamException;
import com.elatusdev.pokedex.catalog.domain.UpstreamUnavailableException;
import com.elatusdev.pokedex.pokedex.domain.Pokemon;
import com.elatusdev.pokedex.shared.domain.ReplicatedFields;
import com.elatusdev.pokedex.catalog.domain.PokemonCatalog;
import com.elatusdev.pokedex.pokedex.domain.PokemonRepository;
import com.elatusdev.pokedex.shared.domain.Category;
import com.elatusdev.pokedex.shared.domain.Height;
import com.elatusdev.pokedex.shared.domain.Mass;
import com.elatusdev.pokedex.shared.domain.PokeApiId;
import com.elatusdev.pokedex.shared.domain.PokemonName;
import com.elatusdev.pokedex.shared.domain.Sprite;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GetPokemonDetailUseCaseTest {

    private final PokemonCatalog catalog = mock(PokemonCatalog.class);
    private final PokemonRepository repository = mock(PokemonRepository.class);
    private final GetPokemonDetailUseCase useCase =
            new GetPokemonDetailUseCase(catalog, repository, new UpstreamOutagePolicy());

    private static Pokemon bulbasaur() {
        return Pokemon.pending(
                PokeApiId.of(1),
                new ReplicatedFields(
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
                        List.of()));
    }

    // the endpoint takes either form, so the use case is where the two are told apart
    @Test
    void should_resolve_a_numeric_reference_as_an_upstream_id() {
        when(catalog.fetchById(PokeApiId.of(1))).thenReturn(Optional.of(bulbasaur()));

        PokemonDetailResult result = useCase.detail("1");

        assertThat(result.pokemon().replicated().name()).isEqualTo(new PokemonName("bulbasaur"));
        assertThat(result.stale()).isFalse();
        verify(catalog, times(1)).fetchById(PokeApiId.of(1));
        verifyNoMoreInteractions(catalog);
        verifyNoInteractions(repository);
    }

    @Test
    void should_resolve_a_non_numeric_reference_as_a_name() {
        when(catalog.fetchByName(new PokemonName("bulbasaur"))).thenReturn(Optional.of(bulbasaur()));

        assertThat(useCase.detail("bulbasaur").pokemon().pokeApiId()).contains(PokeApiId.of(1));

        verify(catalog, times(1)).fetchByName(new PokemonName("bulbasaur"));
        verifyNoMoreInteractions(catalog);
    }

    @Test
    void should_raise_not_found_upstream_when_the_catalogue_has_no_such_pokemon() {
        when(catalog.fetchByName(new PokemonName("missingno"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.detail("missingno"))
                .isInstanceOf(PokemonNotFoundUpstreamException.class)
                .hasMessageContaining("missingno");

        verifyNoInteractions(repository);
    }

    // an absent Pokemon is an answer, not an outage — it must not trigger the stale fallback
    @Test
    void should_not_fall_back_to_the_replica_when_upstream_simply_has_no_such_pokemon() {
        when(catalog.fetchById(PokeApiId.of(9999))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.detail("9999")).isInstanceOf(PokemonNotFoundUpstreamException.class);

        verifyNoInteractions(repository);
    }

    @Test
    void should_fall_back_to_the_replica_and_flag_stale_when_upstream_is_unavailable() {
        when(catalog.fetchById(PokeApiId.of(1))).thenThrow(new UpstreamUnavailableException("pokeapi down", null));
        when(repository.findByPokeApiId(PokeApiId.of(1))).thenReturn(Optional.of(bulbasaur()));

        PokemonDetailResult result = useCase.detail("1");

        assertThat(result.stale()).isTrue();
        verify(repository, times(1)).findByPokeApiId(PokeApiId.of(1));
        verifyNoMoreInteractions(repository);
    }

    @Test
    void should_look_the_replica_up_by_name_when_the_reference_is_a_name() {
        when(catalog.fetchByName(new PokemonName("bulbasaur")))
                .thenThrow(new UpstreamUnavailableException("pokeapi down", null));
        when(repository.findByName(new PokemonName("bulbasaur"))).thenReturn(Optional.of(bulbasaur()));

        assertThat(useCase.detail("bulbasaur").stale()).isTrue();

        verify(repository, times(1)).findByName(new PokemonName("bulbasaur"));
        verifyNoMoreInteractions(repository);
    }

    @Test
    void should_propagate_the_outage_when_there_is_no_local_copy() {
        UpstreamUnavailableException outage = new UpstreamUnavailableException("pokeapi down", null);
        when(catalog.fetchById(PokeApiId.of(1))).thenThrow(outage);
        when(repository.findByPokeApiId(PokeApiId.of(1))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.detail("1")).isSameAs(outage);
    }

    // blank is malformed input, not "no such Pokemon" — the contract declares minLength 1,
    // so this is a 400 and never a 404
    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void should_reject_a_blank_reference_as_invalid_rather_than_absent(String reference) {
        assertThatThrownBy(() -> useCase.detail(reference))
                .isInstanceOf(InvalidPokemonDataException.class)
                .hasMessageContaining("idOrName");

        verifyNoInteractions(catalog);
        verifyNoInteractions(repository);
    }
}
