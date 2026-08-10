package com.elatusdev.pokedex.pokedex.interfaces;

import static org.assertj.core.api.Assertions.assertThat;

import com.elatusdev.pokedex.pokedex.domain.LocalPokemonFilter;
import com.elatusdev.pokedex.pokedex.domain.LocalPokemonQuery;
import com.elatusdev.pokedex.pokedex.domain.Pokemon;
import com.elatusdev.pokedex.pokedex.domain.PokemonRepository;
import com.elatusdev.pokedex.pokedex.domain.Region;
import com.elatusdev.pokedex.pokedex.domain.Tag;
import com.elatusdev.pokedex.shared.domain.PokemonName;
import com.elatusdev.pokedex.testsupport.PokemonFixture;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

// Against real Postgres, because the thing under test is JPQL. It compiles either way; it
// is only validated when Hibernate builds the query, and the tag predicate correlates
// through a UNIDIRECTIONAL association, which is exactly the shape that goes wrong.
@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LocalPokemonQueryComponentTest {

    private final LocalPokemonQuery query;
    private final PokemonRepository repository;
    private final JdbcTemplate jdbc;

    LocalPokemonQueryComponentTest(
            @Autowired LocalPokemonQuery query,
            @Autowired PokemonRepository repository,
            @Autowired JdbcTemplate jdbc) {
        this.query = query;
        this.repository = repository;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void seed() {
        PokemonFixture.clear(jdbc);
        store("bulbasaur", Region.KANTO, "starter");
        store("charmander", Region.KANTO, "starter");
        store("pikachu", Region.KANTO, "mascot");
        store("chikorita", Region.JOHTO, "starter");
    }

    private void store(String name, Region region, String tag) {
        Pokemon pokemon = PokemonFixture.draft(name);
        pokemon.assignRegion(region);
        pokemon.addTag(new Tag(tag));
        repository.save(pokemon);
    }

    private static LocalPokemonFilter filter(Region region, String tag, String nameContains) {
        return new LocalPokemonFilter(
                Optional.ofNullable(region), Optional.ofNullable(tag).map(Tag::new), Optional.ofNullable(nameContains));
    }

    private static java.util.List<String> namesOf(java.util.List<Pokemon> rows) {
        return rows.stream().map(p -> p.replicated().name().value()).toList();
    }

    @Test
    void should_return_every_record_when_no_filter_is_applied() {
        assertThat(query.count(LocalPokemonFilter.none())).isEqualTo(4L);
        assertThat(namesOf(query.findPage(LocalPokemonFilter.none(), 0, 10)))
                .containsExactlyInAnyOrder("bulbasaur", "charmander", "pikachu", "chikorita");
    }

    @Test
    void should_narrow_by_region() {
        assertThat(namesOf(query.findPage(filter(Region.JOHTO, null, null), 0, 10)))
                .containsExactly("chikorita");
        assertThat(query.count(filter(Region.JOHTO, null, null))).isEqualTo(1L);
    }

    @Test
    void should_narrow_by_tag() {
        assertThat(namesOf(query.findPage(filter(null, "mascot", null), 0, 10)))
                .containsExactly("pikachu");
    }

    @Test
    void should_narrow_by_a_name_substring_regardless_of_case() {
        assertThat(namesOf(query.findPage(filter(null, null, "CHAR"), 0, 10)))
                .containsExactly("charmander");
    }

    // AC-US04-6 — the filters compose, and they narrow rather than widen
    @Test
    void should_compose_every_filter_together() {
        LocalPokemonFilter kantoStarters = filter(Region.KANTO, "starter", "bulba");

        assertThat(namesOf(query.findPage(kantoStarters, 0, 10))).containsExactly("bulbasaur");
        assertThat(query.count(kantoStarters)).isEqualTo(1L);
    }

    // the count must describe the FILTERED set, not the table — otherwise the page metadata
    // advertises pages the filter cannot fill
    @Test
    void should_count_the_filtered_set_rather_than_the_table() {
        assertThat(query.count(filter(Region.KANTO, "starter", null))).isEqualTo(2L);
        assertThat(query.count(LocalPokemonFilter.none())).isEqualTo(4L);
    }

    // a record matching a tag twice must not be returned twice, and must not be counted
    // twice either — the reason the predicate is EXISTS rather than a join
    @Test
    void should_return_a_record_once_even_when_it_carries_several_tags() {
        Pokemon many = PokemonFixture.draft("eevee");
        many.assignRegion(Region.KANTO);
        many.addTag(new Tag("starter"));
        many.addTag(new Tag("evolver"));
        repository.save(many);

        LocalPokemonFilter starters = filter(Region.KANTO, "starter", null);

        assertThat(namesOf(query.findPage(starters, 0, 10)))
                .containsExactlyInAnyOrder("bulbasaur", "charmander", "eevee");
        assertThat(query.count(starters)).isEqualTo(3L);
    }

    @Test
    void should_page_through_the_filtered_set() {
        LocalPokemonFilter kanto = filter(Region.KANTO, null, null);

        assertThat(query.findPage(kanto, 0, 2)).hasSize(2);
        assertThat(query.findPage(kanto, 1, 2)).hasSize(1);
        assertThat(query.count(kanto)).isEqualTo(3L);
    }

    @Test
    void should_return_nothing_when_the_filter_matches_no_record() {
        assertThat(query.findPage(filter(Region.PALDEA, null, null), 0, 10)).isEmpty();
        assertThat(query.count(filter(Region.PALDEA, null, null))).isZero();
    }

    @Test
    void should_read_back_a_complete_aggregate_and_not_a_proxy() {
        Pokemon row = query.findPage(filter(null, null, "bulba"), 0, 10).getFirst();

        assertThat(row.replicated().name()).isEqualTo(new PokemonName("bulbasaur"));
        assertThat(row.tags()).containsExactly(new Tag("starter"));
        assertThat(row.proprietary().region()).contains(Region.KANTO);
    }
}
