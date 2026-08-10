package com.elatusdev.pokedex.pokedex.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.elatusdev.pokedex.pokedex.domain.LocalPokemonFilter;
import com.elatusdev.pokedex.pokedex.domain.LocalPokemonQuery;
import com.elatusdev.pokedex.pokedex.domain.Pokemon;
import com.elatusdev.pokedex.pokedex.domain.Region;
import com.elatusdev.pokedex.pokedex.domain.Tag;
import com.elatusdev.pokedex.shared.domain.InvalidPaginationException;
import com.elatusdev.pokedex.testsupport.PokemonFixture;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListLocalPokemonUseCaseTest {

    private static final LocalPokemonFilter NONE = LocalPokemonFilter.none();

    @Mock
    private LocalPokemonQuery query;

    private ListLocalPokemonUseCase useCase() {
        return new ListLocalPokemonUseCase(query);
    }

    @Test
    void should_return_the_page_with_its_metadata() {
        List<Pokemon> rows = List.of(PokemonFixture.syncedBulbasaur());
        when(query.findPage(NONE, 0, 10)).thenReturn(rows);
        when(query.count(NONE)).thenReturn(1L);

        LocalPokemonPageResult result = useCase().list(NONE, 0, 10);

        assertThat(result.rows()).isEqualTo(rows);
        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(10);
        assertThat(result.totalCount()).isEqualTo(1L);
        verify(query, times(1)).findPage(NONE, 0, 10);
        verify(query, times(1)).count(NONE);
        verifyNoMoreInteractions(query);
    }

    // AC-US04-6 — the total must reflect the FILTER, not the table. A count that ignores
    // the filter produces a last page that does not exist.
    @Test
    void should_count_against_the_same_filter_it_paged_with() {
        LocalPokemonFilter kantoStarters = new LocalPokemonFilter(
                Optional.of(Region.KANTO), Optional.of(new Tag("starter")), Optional.empty());
        when(query.findPage(kantoStarters, 0, 10)).thenReturn(List.of());
        when(query.count(kantoStarters)).thenReturn(3L);

        LocalPokemonPageResult result = useCase().list(kantoStarters, 0, 10);

        assertThat(result.totalCount()).isEqualTo(3L);
        verify(query, times(1)).count(kantoStarters);
    }

    @Test
    void should_pass_a_composed_filter_through_untouched() {
        LocalPokemonFilter composed = new LocalPokemonFilter(
                Optional.of(Region.JOHTO), Optional.of(new Tag("legendary")), Optional.of("chu"));
        when(query.findPage(composed, 1, 25)).thenReturn(List.of());
        when(query.count(composed)).thenReturn(0L);

        useCase().list(composed, 1, 25);

        verify(query, times(1)).findPage(composed, 1, 25);
    }

    // rejected, never clamped: a clamped page lets a caller believe it read everything
    @Test
    void should_reject_a_size_above_the_cap() {
        assertThatThrownBy(() -> useCase().list(NONE, 0, 101))
                .isInstanceOf(InvalidPaginationException.class)
                .hasMessageContaining("100");

        verifyNoInteractions(query);
    }

    @Test
    void should_accept_the_size_exactly_at_the_cap() {
        when(query.findPage(NONE, 0, 100)).thenReturn(List.of());
        when(query.count(NONE)).thenReturn(0L);

        assertThat(useCase().list(NONE, 0, 100).size()).isEqualTo(100);
    }

    @Test
    void should_reject_a_size_below_one() {
        assertThatThrownBy(() -> useCase().list(NONE, 0, 0)).isInstanceOf(InvalidPaginationException.class);

        verifyNoInteractions(query);
    }

    @Test
    void should_reject_a_negative_page() {
        assertThatThrownBy(() -> useCase().list(NONE, -1, 10)).isInstanceOf(InvalidPaginationException.class);

        verifyNoInteractions(query);
    }

    // the exception carries the offending values so the advice can name the cap
    @Test
    void should_carry_the_rejected_page_and_size_on_the_exception() {
        assertThatThrownBy(() -> useCase().list(NONE, -2, 500))
                .isInstanceOf(InvalidPaginationException.class)
                .hasFieldOrPropertyWithValue("page", -2)
                .hasFieldOrPropertyWithValue("size", 500);
    }
}
