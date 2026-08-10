package com.elatusdev.pokedex.shared.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SharedExceptionTest {

    @Test
    void should_carry_the_message_when_domain_data_is_invalid() {
        InvalidPokemonDataException thrown = new InvalidPokemonDataException("mass must be positive, was 0");

        assertThat(thrown).hasMessage("mass must be positive, was 0").hasNoCause();
    }

    @Test
    void should_carry_the_rejected_page_request_when_pagination_is_invalid() {
        InvalidPaginationException thrown = new InvalidPaginationException("size must be 1..100", 0, 101);

        assertThat(thrown.page()).isZero();
        assertThat(thrown.size()).isEqualTo(101);
        assertThat(thrown).hasMessage("size must be 1..100");
    }
}
