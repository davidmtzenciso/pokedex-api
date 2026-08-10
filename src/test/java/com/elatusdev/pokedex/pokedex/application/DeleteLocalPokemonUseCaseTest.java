package com.elatusdev.pokedex.pokedex.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

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
class DeleteLocalPokemonUseCaseTest {

    private static final PokemonId ID = PokemonId.of(7);

    @Mock
    private PokemonRepository repository;

    private DeleteLocalPokemonUseCase useCase() {
        return new DeleteLocalPokemonUseCase(repository);
    }

    // AC-US04-5 — the delete is hard, and children go with it by cascade (ADR-0010)
    @Test
    void should_remove_the_record_when_it_exists() {
        when(repository.findById(ID)).thenReturn(Optional.of(PokemonFixture.syncedBulbasaur()));

        useCase().delete(ID);

        verify(repository, times(1)).findById(ID);
        verify(repository, times(1)).delete(ID);
        verifyNoMoreInteractions(repository);
    }

    // the existence check is what turns a delete of nothing into a 404 rather than a
    // silent 204 that tells the caller a row was removed when none was
    @Test
    void should_reject_a_delete_of_a_record_that_does_not_exist() {
        when(repository.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().delete(ID)).isInstanceOf(PokemonNotFoundException.class);

        verify(repository, times(1)).findById(ID);
        verify(repository, never()).delete(ID);
        verifyNoMoreInteractions(repository);
    }
}
