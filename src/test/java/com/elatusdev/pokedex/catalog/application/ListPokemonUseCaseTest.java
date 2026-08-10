package com.elatusdev.pokedex.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.elatusdev.pokedex.shared.domain.InvalidPaginationException;
import com.elatusdev.pokedex.catalog.domain.UpstreamUnavailableException;
import com.elatusdev.pokedex.shared.domain.ReplicatedFields;
import com.elatusdev.pokedex.catalog.domain.CatalogPage;
import com.elatusdev.pokedex.catalog.domain.PokemonCatalog;
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
import org.junit.jupiter.params.provider.CsvSource;
import com.elatusdev.pokedex.catalog.domain.CatalogPokemon;
import com.elatusdev.pokedex.catalog.domain.LocalReplica;

class ListPokemonUseCaseTest {

    private final PokemonCatalog catalog = mock(PokemonCatalog.class);
    private final LocalReplica repository = mock(LocalReplica.class);
    private final ListPokemonUseCase useCase = new ListPokemonUseCase(catalog, repository, new UpstreamOutagePolicy());

    private static CatalogPokemon row(String name) {
        return CatalogPokemon.upstream(
                PokeApiId.of(1),
                new ReplicatedFields(
                        new PokemonName(name),
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

    @Test
    void should_return_the_upstream_page_when_the_catalogue_answers() {
        when(catalog.fetchPage(0, 10)).thenReturn(new CatalogPage(List.of(row("bulbasaur")), 1351));

        PokemonPageResult result = useCase.list(0, 10);

        assertThat(result.rows()).singleElement().satisfies(pokemon ->
                assertThat(pokemon.replicated().name()).isEqualTo(new PokemonName("bulbasaur")));
        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(10);
        assertThat(result.totalElements()).isEqualTo(1351L);
        assertThat(result.stale()).isFalse();
        verify(catalog, times(1)).fetchPage(0, 10);
        verifyNoMoreInteractions(catalog);
        verifyNoInteractions(repository);
    }

    @Test
    void should_derive_the_total_page_count_from_the_size() {
        when(catalog.fetchPage(0, 10)).thenReturn(new CatalogPage(List.of(row("bulbasaur")), 1351));

        assertThat(useCase.list(0, 10).totalPages()).isEqualTo(136);
    }

    // rejected, never clamped: a clamped response lets a caller believe it received
    // everything it asked for
    @ParameterizedTest
    @CsvSource({"0, 101", "0, 0", "0, -1", "-1, 10"})
    void should_reject_a_page_request_outside_the_declared_bounds(int page, int size) {
        assertThatThrownBy(() -> useCase.list(page, size)).isInstanceOf(InvalidPaginationException.class);

        verifyNoInteractions(catalog);
        verifyNoInteractions(repository);
    }

    @Test
    void should_name_the_cap_when_the_size_is_rejected() {
        assertThatThrownBy(() -> useCase.list(0, 101))
                .isInstanceOf(InvalidPaginationException.class)
                .hasMessageContaining("100");
    }

    // the lower bound needs pinning as much as the upper one: only the maximum was
    // asserted, so a mutant shifting `size < 1` to `size < 2` survived — and it would have
    // rejected every single-row page a client asked for
    @Test
    void should_accept_the_smallest_valid_size() {
        when(catalog.fetchPage(0, 1)).thenReturn(new CatalogPage(List.of(row("bulbasaur")), 1351));

        assertThat(useCase.list(0, 1).rows()).hasSize(1);
    }

    @Test
    void should_accept_the_maximum_size() {
        when(catalog.fetchPage(0, 100)).thenReturn(new CatalogPage(List.of(row("bulbasaur")), 1351));

        assertThat(useCase.list(0, 100).rows()).hasSize(1);
    }

    // AC-US01-5 — an upstream outage with a local replica degrades to stale data rather
    // than an error, because a stale Pokedex still answers the question
    @Test
    void should_fall_back_to_the_local_replica_and_flag_stale_when_upstream_is_unavailable() {
        UpstreamUnavailableException outage = new UpstreamUnavailableException("pokeapi down", null);
        when(catalog.fetchPage(0, 10)).thenThrow(outage);
        when(repository.findPage(0, 10)).thenReturn(List.of(row("bulbasaur")));
        when(repository.count()).thenReturn(151L);

        PokemonPageResult result = useCase.list(0, 10);

        assertThat(result.stale()).isTrue();
        assertThat(result.rows()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(151L);
        verify(catalog, times(1)).fetchPage(0, 10);
        verify(repository, times(1)).findPage(0, 10);
        verify(repository, times(1)).count();
        verifyNoMoreInteractions(catalog, repository);
    }

    // AC-US01-6 — with nothing local to serve, the outage is the answer
    @Test
    void should_propagate_the_outage_when_there_is_no_local_copy() {
        UpstreamUnavailableException outage = new UpstreamUnavailableException("pokeapi down", null);
        when(catalog.fetchPage(0, 10)).thenThrow(outage);
        when(repository.findPage(0, 10)).thenReturn(List.of());

        assertThatThrownBy(() -> useCase.list(0, 10)).isSameAs(outage);

        verify(repository, times(1)).findPage(0, 10);
        verifyNoMoreInteractions(repository);
    }
}
