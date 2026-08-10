package com.elatusdev.pokedex.catalog.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class CatalogExceptionTest {

    @Test
    void should_carry_the_reference_when_upstream_has_no_such_pokemon() {
        PokemonNotFoundUpstreamException thrown = new PokemonNotFoundUpstreamException("missingno");

        assertThat(thrown.reference()).isEqualTo("missingno");
        assertThat(thrown).hasMessage("PokeAPI has no Pokemon 'missingno'");
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
}
