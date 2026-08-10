package com.elatusdev.pokedex.pokedex.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.elatusdev.pokedex.pokedex.domain.PokemonRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import com.elatusdev.pokedex.pokedex.domain.Pokemon;
import com.elatusdev.pokedex.testsupport.PokemonFixture;

// WU-US03-A's exit criterion says a page of 10 should issue "one" query. It cannot, and the
// number it should issue is worth being precise about.
//
// A Pokemon has six child collections. Join-fetching them all in the page query multiplies
// their rows together and, with a limit/offset, forces Hibernate to paginate in memory —
// it fetches every row in the table and throws away all but ten. Fetching none of them is
// the N+1: 1 + 6x10 = 61 queries for a page of ten.
//
// @BatchSize takes the third option: one query for the page, then one per collection for the
// whole page at once. Seven queries, and — the part that matters — seven for a page of ten
// and seven for a page of a hundred. The count is bounded by the number of collections, not
// by the page size, which is the property "no N+1" actually means.
@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PokemonPageQueryCountComponentTest {

    private static final int COLLECTIONS_PER_POKEMON = 6;
    private static final int PAGE_QUERY = 1;

    private final PokemonRepository repository;
    private final JdbcTemplate jdbc;
    private final Statistics statistics;

    PokemonPageQueryCountComponentTest(
            @Autowired PokemonRepository repository,
            @Autowired JdbcTemplate jdbc,
            @Autowired EntityManagerFactory entityManagerFactory) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    @BeforeEach
    void clearDatabase() {
        PokemonFixture.clear(jdbc);
        statistics.setStatisticsEnabled(true);
    }

    @Test
    void should_issue_one_query_per_collection_when_a_page_of_ten_is_read() {
        savePokemon(10);
        statistics.clear();

        assertThat(repository.findPage(0, 10)).hasSize(10);

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(PAGE_QUERY + COLLECTIONS_PER_POKEMON);
    }

    @Test
    void should_issue_the_same_number_of_queries_when_the_page_is_five_times_larger() {
        savePokemon(50);
        statistics.clear();

        assertThat(repository.findPage(0, 50)).hasSize(50);

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(PAGE_QUERY + COLLECTIONS_PER_POKEMON);
    }

    private void savePokemon(int count) {
        for (int pokeApiId = 1; pokeApiId <= count; pokeApiId++) {
            repository.save(PokemonFixture.synced(pokeApiId, PokemonFixture.bulbasaur()));
        }
    }
}
