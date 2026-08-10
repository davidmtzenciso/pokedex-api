package com.elatusdev.pokedex.pokedex.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.elatusdev.pokedex.pokedex.domain.Pokemon;
import com.elatusdev.pokedex.pokedex.domain.PokemonId;
import com.elatusdev.pokedex.pokedex.domain.PokemonNotFoundException;
import com.elatusdev.pokedex.pokedex.domain.PokemonRepository;
import com.elatusdev.pokedex.testsupport.PokemonFixture;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetLocalPokemonUseCaseTest {

    private static final PokemonId ID = PokemonId.of(7);

    @Mock
    private PokemonRepository repository;

    private GetLocalPokemonUseCase useCase() {
        return new GetLocalPokemonUseCase(repository);
    }

    @Test
    void should_return_the_record_when_the_id_exists() {
        Pokemon stored = PokemonFixture.syncedBulbasaur();
        when(repository.findById(ID)).thenReturn(Optional.of(stored));

        assertThat(useCase().get(ID)).isSameAs(stored);

        verify(repository, times(1)).findById(ID);
        verifyNoMoreInteractions(repository);
    }

    // AC-US04-2 — the 404 the story names explicitly
    @Test
    void should_reject_an_id_that_does_not_exist() {
        when(repository.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().get(ID))
                .isInstanceOf(PokemonNotFoundException.class)
                .hasMessageContaining("7");

        verify(repository, times(1)).findById(ID);
        verifyNoMoreInteractions(repository);
    }
}
