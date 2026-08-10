package com.elatusdev.pokedex.pokedex.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.elatusdev.pokedex.pokedex.domain.port.PokemonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

// I1 / F1. Both halves matter and only one of them is obvious: the index must reject a
// duplicate poke_api_id, and it must still permit any number of DRAFT rows that have none.
// A plain UNIQUE constraint passes the first test and fails the second, which is exactly the
// bug the partial predicate exists to prevent.
@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PokemonUniquenessComponentTest {

    private final PokemonRepository repository;
    private final JdbcTemplate jdbc;

    PokemonUniquenessComponentTest(@Autowired PokemonRepository repository, @Autowired JdbcTemplate jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void clearDatabase() {
        PokemonFixture.clear(jdbc);
    }

    @Test
    void should_reject_the_second_row_when_a_poke_api_id_is_already_replicated() {
        repository.save(PokemonFixture.syncedBulbasaur());

        assertThatThrownBy(() -> repository.save(PokemonFixture.synced(1, PokemonFixture.bulbasaur())))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ux_pokemon_poke_api_id");

        assertThat(rowCount()).isEqualTo(1);
    }

    @Test
    void should_permit_many_rows_when_none_of_them_is_linked_to_upstream() {
        repository.save(PokemonFixture.draft("first"));
        repository.save(PokemonFixture.draft("second"));
        repository.save(PokemonFixture.draft("third"));

        assertThat(rowCount()).isEqualTo(3);
    }

    @Test
    void should_permit_a_second_row_when_the_poke_api_id_differs() {
        repository.save(PokemonFixture.synced(1, PokemonFixture.bulbasaur()));
        repository.save(PokemonFixture.synced(2, PokemonFixture.bulbasaur()));

        assertThat(rowCount()).isEqualTo(2);
    }

    private int rowCount() {
        return jdbc.queryForObject("SELECT count(*) FROM pokemon", Integer.class);
    }
}
