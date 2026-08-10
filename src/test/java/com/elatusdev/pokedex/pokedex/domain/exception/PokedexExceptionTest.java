package com.elatusdev.pokedex.pokedex.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.elatusdev.pokedex.pokedex.domain.model.ReplicationState;
import com.elatusdev.pokedex.pokedex.domain.vo.PokemonId;
import com.elatusdev.pokedex.shared.domain.vo.PokeApiId;
import org.junit.jupiter.api.Test;

// Each type exists because it maps to a distinct response code, and each carries its context
// as an accessor rather than only inside the message — the handler needs the value, not prose.
class PokedexExceptionTest {

    @Test
    void should_carry_the_pokemon_id_when_a_local_record_is_missing() {
        PokemonNotFoundException thrown = new PokemonNotFoundException(PokemonId.of(9999));

        assertThat(thrown.id()).isEqualTo(PokemonId.of(9999));
        assertThat(thrown).hasMessage("No Pokemon with id 9999");
    }

    @Test
    void should_carry_the_upstream_id_when_the_pokeapi_id_is_already_replicated() {
        DuplicatePokemonException thrown = new DuplicatePokemonException(PokeApiId.of(1));

        assertThat(thrown.pokeApiId()).isEqualTo(PokeApiId.of(1));
        assertThat(thrown).hasMessage("A Pokemon with pokeApiId 1 is already replicated");
    }

    @Test
    void should_carry_both_ends_of_the_edge_when_a_transition_is_illegal() {
        IllegalStateTransitionException thrown =
                new IllegalStateTransitionException(ReplicationState.SYNCED, ReplicationState.PENDING);

        assertThat(thrown.from()).isEqualTo(ReplicationState.SYNCED);
        assertThat(thrown.to()).isEqualTo(ReplicationState.PENDING);
        assertThat(thrown).hasMessage("Illegal replication transition SYNCED -> PENDING");
    }
}
