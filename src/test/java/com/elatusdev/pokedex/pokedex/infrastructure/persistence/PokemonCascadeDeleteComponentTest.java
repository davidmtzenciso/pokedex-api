package com.elatusdev.pokedex.pokedex.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.elatusdev.pokedex.pokedex.domain.model.Pokemon;
import com.elatusdev.pokedex.pokedex.domain.port.PokemonRepository;
import com.elatusdev.pokedex.identity.domain.port.UserRepository;
import com.elatusdev.pokedex.pokedex.domain.vo.PokemonId;
import com.elatusdev.pokedex.pokedex.domain.vo.Region;
import com.elatusdev.pokedex.pokedex.domain.vo.Tag;
import com.elatusdev.pokedex.identity.domain.vo.UserId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import com.elatusdev.pokedex.testsupport.PokemonFixture;

// I9 / F10 — delete(p) ⟹ p ∉ Pokemon ∧ ∀c ∈ children(p): c ∉ Child.
// Deletes are hard (ADR-0010): there is nothing to filter out afterwards, so an orphan left
// behind is a row that never goes away and shows up in the next count.
@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PokemonCascadeDeleteComponentTest {

    private static final List<String> CHILD_TABLES = List.of(
            "pokemon_ability", "pokemon_stat", "pokemon_type", "pokemon_tag", "localized_name", "evolution_link");

    private final PokemonRepository repository;
    private final UserRepository users;
    private final JdbcTemplate jdbc;

    PokemonCascadeDeleteComponentTest(
            @Autowired PokemonRepository repository, @Autowired UserRepository users, @Autowired JdbcTemplate jdbc) {
        this.repository = repository;
        this.users = users;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void clearDatabase() {
        PokemonFixture.clear(jdbc);
    }

    @Test
    void should_leave_no_child_row_when_the_pokemon_is_deleted() {
        Pokemon pokemon = PokemonFixture.syncedBulbasaur();
        pokemon.addTag(new Tag("starter"));
        PokemonId id = repository.save(pokemon).id().orElseThrow();
        assertThat(childRowCounts()).allSatisfy((table, count) -> assertThat(count)
                .describedAs("%s should have rows before the delete", table)
                .isPositive());

        repository.delete(id);

        assertThat(repository.findById(id)).isEmpty();
        assertThat(childRowCounts()).allSatisfy((table, count) -> assertThat(count)
                .describedAs("orphans left in %s", table)
                .isZero());
    }

    @Test
    void should_leave_another_pokemons_children_untouched_when_one_is_deleted() {
        PokemonId doomed = save(tagged(1)).id().orElseThrow();
        PokemonId survivor = save(tagged(2)).id().orElseThrow();

        repository.delete(doomed);

        assertThat(repository.findById(survivor)).isPresent();
        assertThat(childRowCounts()).allSatisfy((table, count) -> assertThat(count)
                .describedAs("%s lost the surviving Pokemon's rows", table)
                .isPositive());
    }

    // the curator crosses an aggregate boundary, so this FK is the one that must NOT cascade
    @Test
    void should_keep_the_pokemon_and_clear_its_curator_when_the_user_is_deleted() {
        UserId curator = users.save(PokemonFixture.curator("brock")).id().orElseThrow();
        Pokemon pokemon = PokemonFixture.syncedBulbasaur();
        pokemon.assignRegion(Region.KANTO);
        pokemon.curateBy(curator);
        PokemonId id = repository.save(pokemon).id().orElseThrow();

        jdbc.update("DELETE FROM users WHERE id = ?", curator.value());

        Pokemon found = repository.findById(id).orElseThrow();
        assertThat(found.curatedBy()).isEmpty();
        assertThat(found.proprietary().region()).contains(Region.KANTO);
    }

    @Test
    void should_delete_nothing_when_the_id_does_not_exist() {
        repository.save(PokemonFixture.syncedBulbasaur());

        repository.delete(PokemonId.of(9999));

        assertThat(repository.count()).isEqualTo(1L);
    }

    private Pokemon tagged(int pokeApiId) {
        Pokemon pokemon = PokemonFixture.synced(pokeApiId, PokemonFixture.bulbasaur());
        pokemon.addTag(new Tag("starter"));
        return pokemon;
    }

    private Pokemon save(Pokemon pokemon) {
        return repository.save(pokemon);
    }

    private Map<String, Integer> childRowCounts() {
        return CHILD_TABLES.stream()
                .collect(Collectors.toMap(
                        table -> table,
                        table -> jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class)));
    }
}
