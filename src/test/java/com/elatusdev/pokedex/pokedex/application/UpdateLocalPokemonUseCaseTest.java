package com.elatusdev.pokedex.pokedex.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.elatusdev.pokedex.pokedex.domain.Notes;
import com.elatusdev.pokedex.pokedex.domain.Pokemon;
import com.elatusdev.pokedex.pokedex.domain.PokemonId;
import com.elatusdev.pokedex.pokedex.domain.PokemonNotFoundException;
import com.elatusdev.pokedex.pokedex.domain.PokemonRepository;
import com.elatusdev.pokedex.pokedex.domain.Region;
import com.elatusdev.pokedex.pokedex.domain.ReplicationState;
import com.elatusdev.pokedex.pokedex.domain.Tag;
import com.elatusdev.pokedex.shared.domain.ClockPort;
import com.elatusdev.pokedex.testsupport.PokemonFixture;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class UpdateLocalPokemonUseCaseTest {

    private static final PokemonId ID = PokemonId.of(7);

    private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");

    @Mock
    private PokemonRepository repository;

    @Mock
    private ClockPort clock;

    private UpdateLocalPokemonUseCase useCase() {
        return new UpdateLocalPokemonUseCase(repository, clock);
    }

    private static UpdateLocalPokemonCommand command(long version) {
        return new UpdateLocalPokemonCommand(
                version,
                Optional.of(Region.KANTO),
                Optional.of(new Notes("Route 1 favourite")),
                List.of(new Tag("starter")),
                List.of());
    }

    // AC-US04-1 — region and tags applied, and the record becomes CUSTOMIZED
    @Test
    void should_apply_the_proprietary_fields_and_mark_the_record_customised() {
        Pokemon stored = PokemonFixture.syncedBulbasaur();
        when(repository.findById(ID)).thenReturn(Optional.of(stored));
        when(repository.save(stored)).thenReturn(stored);
        when(clock.now()).thenReturn(NOW);

        Pokemon result = useCase().update(ID, command(stored.version()));

        assertThat(result.proprietary().region()).contains(Region.KANTO);
        assertThat(result.tags()).containsExactly(new Tag("starter"));
        assertThat(result.proprietary().notes()).contains(new Notes("Route 1 favourite"));
        assertThat(result.replicationState()).isEqualTo(ReplicationState.CUSTOMIZED);
        verify(repository, times(1)).findById(ID);
        verify(repository, times(1)).save(stored);
        verifyNoMoreInteractions(repository);
    }

    // AC-US04-2 — the 404 the story names explicitly
    @Test
    void should_reject_an_id_that_does_not_exist() {
        when(repository.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().update(ID, command(0)))
                .isInstanceOf(PokemonNotFoundException.class);

        verify(repository, never()).save(argThat(p -> true));
    }

    // AC-US04-4 — 412, and deliberately NOT 409: "someone changed it since you read it" is
    // a different condition from "that already exists", and the client does different things
    @Test
    void should_reject_a_stale_version_without_writing() {
        Pokemon stored = PokemonFixture.syncedBulbasaur();
        when(repository.findById(ID)).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> useCase().update(ID, command(stored.version() + 1)))
                .isInstanceOf(OptimisticLockingFailureException.class)
                .hasMessageContaining("7");

        verify(repository, times(1)).findById(ID);
        verifyNoMoreInteractions(repository);
    }

    // DRAFT -> CUSTOMIZED is not a legal edge (only SYNCED, CUSTOMIZED and STALE reach it).
    // Editing a draft must still work, so the transition is conditional rather than blind —
    // a blind transitionTo here would throw IllegalStateTransitionException on every draft.
    @Test
    void should_leave_a_draft_in_draft_when_its_proprietary_fields_are_edited() {
        Pokemon draft = PokemonFixture.draft("bulbasaur");
        when(repository.findById(ID)).thenReturn(Optional.of(draft));
        when(repository.save(draft)).thenReturn(draft);

        Pokemon result = useCase().update(ID, command(draft.version()));

        assertThat(result.replicationState()).isEqualTo(ReplicationState.DRAFT);
        assertThat(result.proprietary().region()).contains(Region.KANTO);
    }

    @Test
    void should_keep_a_customised_record_customised() {
        Pokemon stored = PokemonFixture.syncedBulbasaur();
        stored.assignRegion(Region.JOHTO);
        when(repository.findById(ID)).thenReturn(Optional.of(stored));
        when(repository.save(stored)).thenReturn(stored);
        when(clock.now()).thenReturn(NOW);

        Pokemon result = useCase().update(ID, command(stored.version()));

        assertThat(result.replicationState()).isEqualTo(ReplicationState.CUSTOMIZED);
        assertThat(result.proprietary().region()).contains(Region.KANTO);
    }

    // tags are REPLACED, not appended. Without clearing first, an update that drops a tag
    // silently keeps it, and the record accumulates tags no curator asked for.
    @Test
    void should_replace_the_existing_tags_rather_than_add_to_them() {
        Pokemon stored = PokemonFixture.syncedBulbasaur();
        stored.addTag(new Tag("obsolete"));
        when(repository.findById(ID)).thenReturn(Optional.of(stored));
        when(repository.save(stored)).thenReturn(stored);
        when(clock.now()).thenReturn(NOW);

        Pokemon result = useCase().update(ID, command(stored.version()));

        assertThat(result.tags()).containsExactly(new Tag("starter"));
    }

    // R13 — the command carries proprietary fields only, so a replicated field cannot even
    // be expressed here. This pins that: adding one would change the component list.
    @Test
    void should_accept_no_replicated_field_on_the_command() {
        assertThat(UpdateLocalPokemonCommand.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("version", "region", "notes", "tags", "curatorNames");
    }
}
