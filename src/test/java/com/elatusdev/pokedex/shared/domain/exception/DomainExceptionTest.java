package com.elatusdev.pokedex.shared.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.elatusdev.pokedex.pokedex.domain.model.ReplicationState;
import com.elatusdev.pokedex.shared.domain.vo.PokeApiId;
import com.elatusdev.pokedex.pokedex.domain.vo.PokemonId;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import com.elatusdev.pokedex.pokedex.domain.exception.PokemonNotFoundException;
import com.elatusdev.pokedex.pokedex.domain.exception.IllegalStateTransitionException;
import com.elatusdev.pokedex.pokedex.domain.exception.DuplicatePokemonException;
import com.elatusdev.pokedex.catalog.domain.exception.PokemonNotFoundUpstreamException;
import com.elatusdev.pokedex.identity.domain.exception.UserAlreadyExistsException;
import com.elatusdev.pokedex.identity.domain.exception.TokenReuseDetectedException;
import com.elatusdev.pokedex.pokedex.domain.model.Pokemon;
import com.elatusdev.pokedex.catalog.domain.exception.UpstreamUnavailableException;
import com.elatusdev.pokedex.catalog.domain.exception.UpstreamTimeoutException;

// Each type exists because it maps to a distinct response code, and each carries its context
// as an accessor rather than only inside the message — the handler needs the value, not prose.
class DomainExceptionTest {

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
    void should_carry_the_reference_when_upstream_has_no_such_pokemon() {
        PokemonNotFoundUpstreamException thrown = new PokemonNotFoundUpstreamException("missingno");

        assertThat(thrown.reference()).isEqualTo("missingno");
        assertThat(thrown).hasMessage("PokeAPI has no Pokemon 'missingno'");
    }

    @Test
    void should_carry_both_ends_of_the_edge_when_a_transition_is_illegal() {
        IllegalStateTransitionException thrown =
                new IllegalStateTransitionException(ReplicationState.SYNCED, ReplicationState.PENDING);

        assertThat(thrown.from()).isEqualTo(ReplicationState.SYNCED);
        assertThat(thrown.to()).isEqualTo(ReplicationState.PENDING);
        assertThat(thrown).hasMessage("Illegal replication transition SYNCED -> PENDING");
    }

    @Test
    void should_carry_the_family_when_a_rotated_refresh_token_is_replayed() {
        TokenReuseDetectedException thrown = new TokenReuseDetectedException("family-7");

        assertThat(thrown.familyId()).isEqualTo("family-7");
        assertThat(thrown).hasMessage("Refresh token reuse detected; family revoked");
    }

    // the family id identifies the revocation target; it is not a secret and not the token
    @Test
    void should_not_echo_the_replayed_token_in_the_message() {
        TokenReuseDetectedException thrown = new TokenReuseDetectedException("family-7");

        assertThat(thrown.getMessage()).doesNotContain("family-7");
    }

    @Test
    void should_carry_the_conflicting_field_when_a_user_already_exists() {
        UserAlreadyExistsException thrown = new UserAlreadyExistsException("username");

        assertThat(thrown.field()).isEqualTo("username");
        assertThat(thrown).hasMessage("A user with that username already exists");
    }

    @Test
    void should_carry_the_cause_when_upstream_times_out() {
        IOException cause = new IOException("read timed out");

        UpstreamTimeoutException thrown = new UpstreamTimeoutException("pokeapi timed out", cause);

        assertThat(thrown).hasMessage("pokeapi timed out").hasCause(cause);
    }

    @Test
    void should_carry_the_cause_when_upstream_is_unavailable() {
        IOException cause = new IOException("connection refused");

        UpstreamUnavailableException thrown = new UpstreamUnavailableException("pokeapi unavailable", cause);

        assertThat(thrown).hasMessage("pokeapi unavailable").hasCause(cause);
    }

    @Test
    void should_carry_the_message_when_domain_data_is_invalid() {
        InvalidPokemonDataException thrown = new InvalidPokemonDataException("mass must be positive, was 0");

        assertThat(thrown).hasMessage("mass must be positive, was 0").hasNoCause();
    }
}
