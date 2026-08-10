package com.elatusdev.pokedex.pokedex.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.elatusdev.pokedex.pokedex.domain.DuplicatePokemonException;
import com.elatusdev.pokedex.pokedex.domain.Notes;
import com.elatusdev.pokedex.pokedex.domain.Pokemon;
import com.elatusdev.pokedex.pokedex.domain.PokemonRepository;
import com.elatusdev.pokedex.pokedex.domain.Region;
import com.elatusdev.pokedex.pokedex.domain.ReplicationState;
import com.elatusdev.pokedex.pokedex.domain.Tag;
import com.elatusdev.pokedex.shared.domain.Height;
import com.elatusdev.pokedex.shared.domain.Mass;
import com.elatusdev.pokedex.shared.domain.PokeApiId;
import com.elatusdev.pokedex.shared.domain.PokemonName;
import com.elatusdev.pokedex.testsupport.PokemonFixture;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreateLocalPokemonUseCaseTest {

    private static final PokeApiId UPSTREAM = PokeApiId.of(1);

    @Mock
    private PokemonRepository repository;

    private CreateLocalPokemonUseCase useCase() {
        return new CreateLocalPokemonUseCase(repository);
    }

    private static CreateLocalPokemonCommand command(Optional<PokeApiId> pokeApiId) {
        return new CreateLocalPokemonCommand(
                new PokemonName("bulbasaur"),
                pokeApiId,
                Mass.ofHectograms(69),
                Height.ofDecimetres(7),
                Optional.empty(),
                Optional.empty(),
                Optional.of(Region.KANTO),
                Optional.of(new Notes("Route 1 favourite")),
                List.of(new Tag("starter")));
    }

    // Pokemon is an aggregate with no equals(), so the predicate covers every field it
    // asserts — as strict as equality, not an any() matcher in disguise
    private static Pokemon draftNamed(String name, Region region, String tag) {
        return argThat(p -> p.id().isEmpty()
                && p.pokeApiId().isEmpty()
                && p.replicationState() == ReplicationState.DRAFT
                && p.replicated().name().equals(new PokemonName(name))
                && p.proprietary().region().equals(Optional.of(region))
                && p.proprietary().notes().equals(Optional.of(new Notes("Route 1 favourite")))
                && p.tags().equals(List.of(new Tag(tag))));
    }

    @Test
    void should_store_a_draft_when_no_upstream_id_is_given() {
        Pokemon saved = PokemonFixture.draft("bulbasaur");
        when(repository.save(draftNamed("bulbasaur", Region.KANTO, "starter"))).thenReturn(saved);

        assertThat(useCase().create(command(Optional.empty()))).isSameAs(saved);

        verify(repository, times(1)).save(draftNamed("bulbasaur", Region.KANTO, "starter"));
        verifyNoMoreInteractions(repository);
    }

    // F6 — DRAFT is exactly the set of unlinked records. Supplying an upstream id links the
    // record instead, which moves it to PENDING; leaving it DRAFT would break the invariant.
    @Test
    void should_link_to_upstream_and_leave_draft_when_an_upstream_id_is_given() {
        when(repository.existsByPokeApiId(UPSTREAM)).thenReturn(false);
        when(repository.save(argThat(p -> p.replicationState() == ReplicationState.PENDING
                        && p.pokeApiId().equals(Optional.of(UPSTREAM)))))
                .thenReturn(PokemonFixture.syncedBulbasaur());

        useCase().create(command(Optional.of(UPSTREAM)));

        verify(repository, times(1)).existsByPokeApiId(UPSTREAM);
        verify(repository, times(1))
                .save(argThat(p -> p.replicationState() == ReplicationState.PENDING
                        && p.pokeApiId().equals(Optional.of(UPSTREAM))));
        verifyNoMoreInteractions(repository);
    }

    // "already exists" is not "not found", and it is not "someone changed it either" — 409
    @Test
    void should_reject_an_upstream_id_that_is_already_replicated() {
        when(repository.existsByPokeApiId(UPSTREAM)).thenReturn(true);

        assertThatThrownBy(() -> useCase().create(command(Optional.of(UPSTREAM))))
                .isInstanceOf(DuplicatePokemonException.class);

        verify(repository, times(1)).existsByPokeApiId(UPSTREAM);
        // nothing is written when the id is taken
        verifyNoMoreInteractions(repository);
    }

    @Test
    void should_not_check_for_a_duplicate_when_there_is_no_upstream_id() {
        when(repository.save(draftNamed("bulbasaur", Region.KANTO, "starter")))
                .thenReturn(PokemonFixture.draft("bulbasaur"));

        useCase().create(command(Optional.empty()));

        verify(repository, times(1)).save(draftNamed("bulbasaur", Region.KANTO, "starter"));
        verifyNoMoreInteractions(repository);
    }
}
