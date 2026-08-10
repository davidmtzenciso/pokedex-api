package com.elatusdev.pokedex.pokedex.interfaces;

import static org.assertj.core.api.Assertions.assertThat;

import com.elatusdev.pokedex.contract.dto.ProblemDetailDTO;
import com.elatusdev.pokedex.pokedex.domain.DuplicatePokemonException;
import com.elatusdev.pokedex.pokedex.domain.IllegalStateTransitionException;
import com.elatusdev.pokedex.pokedex.domain.PokemonId;
import com.elatusdev.pokedex.pokedex.domain.PokemonNotFoundException;
import com.elatusdev.pokedex.pokedex.domain.ReplicationState;
import com.elatusdev.pokedex.shared.domain.PokeApiId;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

// Every row of the WF-US03 §9.5 error table that names a pokedex exception, asserting the
// exact status AND the code. The code is the contract — clients branch on it, and prose
// changes while codes do not.
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PokedexExceptionHandlerTest {

    private final PokedexExceptionHandler handler = new PokedexExceptionHandler();

    @Test
    void should_answer_404_pokemon_not_found_when_no_such_record_exists() {
        ResponseEntity<ProblemDetailDTO> response = handler.onNotFound(new PokemonNotFoundException(PokemonId.of(9999)));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().getCode()).isEqualTo("POKEMON_NOT_FOUND");
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody().getDetail()).contains("9999");
    }

    @Test
    void should_answer_404_when_upstream_has_no_such_pokemon() {
        ResponseEntity<ProblemDetailDTO> response = handler.onNotFound(new PokemonNotFoundException("missingno"));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().getCode()).isEqualTo("POKEMON_NOT_FOUND");
        assertThat(response.getBody().getDetail()).contains("missingno");
    }

    // re-sync on a record that is neither STALE nor FAILED
    @Test
    void should_answer_409_illegal_state_transition_when_the_edge_is_not_legal() {
        ResponseEntity<ProblemDetailDTO> response = handler.onIllegalTransition(
                new IllegalStateTransitionException(ReplicationState.SYNCED, ReplicationState.STALE));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().getCode()).isEqualTo("ILLEGAL_STATE_TRANSITION");
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void should_answer_409_duplicate_pokemon_when_the_poke_api_id_is_already_replicated() {
        ResponseEntity<ProblemDetailDTO> response =
                handler.onDuplicate(new DuplicatePokemonException(PokeApiId.of(1)));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().getCode()).isEqualTo("DUPLICATE_POKEMON");
    }

    // 412, not 409: the request was well formed and the caller simply held an old version,
    // and the difference tells them to reload rather than to change what they sent
    @Test
    void should_answer_412_stale_version_when_a_concurrent_edit_won() {
        ResponseEntity<ProblemDetailDTO> response =
                handler.onStaleVersion(new OptimisticLockingFailureException("row was updated"));

        assertThat(response.getStatusCode().value()).isEqualTo(412);
        assertThat(response.getBody().getCode()).isEqualTo("STALE_VERSION");
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void should_carry_a_trace_id_and_timestamp_on_every_problem() {
        ProblemDetailDTO body =
                handler.onNotFound(new PokemonNotFoundException(PokemonId.of(1))).getBody();

        assertThat(body.getTraceId()).hasSize(32);
        assertThat(body.getTimestamp()).isNotNull();
        assertThat(body.getType().toString()).startsWith("https://pokedex.elatus-dev.com/problems/");
    }
}
