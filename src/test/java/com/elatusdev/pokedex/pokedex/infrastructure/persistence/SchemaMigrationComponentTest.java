package com.elatusdev.pokedex.pokedex.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import com.elatusdev.pokedex.identity.domain.model.User;

// Flyway against a real Postgres 17, from empty. H2 would apply a migration that Postgres
// rejects and a partial index it silently reinterprets, so this tier is the only place the
// schema is actually proven.
@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SchemaMigrationComponentTest {

    private static final List<String> EXPECTED_TABLES = List.of(
            "evolution_link",
            "localized_name",
            "pokemon",
            "pokemon_ability",
            "pokemon_stat",
            "pokemon_tag",
            "pokemon_type",
            "refresh_tokens",
            "users");

    private final JdbcTemplate jdbc;

    SchemaMigrationComponentTest(@Autowired JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Test
    void should_record_a_successful_v1_when_flyway_applies_from_empty() {
        List<String> applied = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank",
                String.class);

        assertThat(applied).containsExactly("1");
    }

    @Test
    void should_create_the_nine_tables_when_the_migration_applies() {
        List<String> tables = jdbc.queryForList(
                """
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
                  AND table_name <> 'flyway_schema_history'
                ORDER BY table_name
                """,
                String.class);

        assertThat(tables).isEqualTo(EXPECTED_TABLES);
    }

    // I1/F1 — a plain unique constraint would permit exactly one DRAFT row, because every
    // DRAFT has poke_api_id NULL and NULL is distinct from itself only under a partial index
    @Test
    void should_index_poke_api_id_partially_when_the_row_is_linked_to_upstream() {
        String definition = jdbc.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE indexname = 'ux_pokemon_poke_api_id'", String.class);

        assertThat(definition)
                .contains("CREATE UNIQUE INDEX")
                .contains("ON public.pokemon")
                .contains("(poke_api_id)")
                .contains("WHERE (poke_api_id IS NOT NULL)");
    }

    // ADR-0010 — deletes are hard. A deleted_at column anywhere is the start of a filter on
    // every query and a class of bug where one forgotten predicate resurrects deleted data.
    @Test
    void should_carry_no_soft_delete_column_when_the_schema_is_complete() {
        List<String> softDeleteColumns = jdbc.queryForList(
                """
                SELECT table_name || '.' || column_name FROM information_schema.columns
                WHERE table_schema = 'public' AND column_name IN ('deleted_at', 'deleted', 'is_deleted')
                """,
                String.class);

        assertThat(softDeleteColumns).isEmpty();
    }

    // F10 — the cascade is declared in the schema as well as on the aggregate, so a row
    // deleted outside JPA cannot leave orphans either
    @Test
    void should_cascade_on_delete_when_the_table_is_a_child_of_pokemon() {
        List<String> nonCascading = jdbc.queryForList(
                """
                SELECT tc.table_name || '.' || tc.constraint_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.referential_constraints rc
                  ON rc.constraint_name = tc.constraint_name AND rc.constraint_schema = tc.table_schema
                JOIN information_schema.constraint_column_usage ccu
                  ON ccu.constraint_name = tc.constraint_name AND ccu.constraint_schema = tc.table_schema
                WHERE tc.constraint_type = 'FOREIGN KEY' AND tc.table_schema = 'public'
                  AND ccu.table_name = 'pokemon' AND rc.delete_rule <> 'CASCADE'
                """,
                String.class);

        assertThat(nonCascading).isEmpty();
    }

    @Test
    void should_reference_pokemon_from_every_child_table_when_the_schema_is_complete() {
        List<String> childTables = jdbc.queryForList(
                """
                SELECT DISTINCT tc.table_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.constraint_column_usage ccu
                  ON ccu.constraint_name = tc.constraint_name AND ccu.constraint_schema = tc.table_schema
                WHERE tc.constraint_type = 'FOREIGN KEY' AND tc.table_schema = 'public'
                  AND ccu.table_name = 'pokemon'
                ORDER BY tc.table_name
                """,
                String.class);

        assertThat(childTables)
                .containsExactly(
                        "evolution_link", "localized_name", "pokemon_ability", "pokemon_stat", "pokemon_tag",
                        "pokemon_type");
    }

    // the curator is a different aggregate, so the reference is id-only and must NOT cascade:
    // deleting a user is not a reason to delete the records they curated
    @Test
    void should_null_the_curator_when_the_referenced_user_is_deleted() {
        String deleteRule = jdbc.queryForObject(
                """
                SELECT rc.delete_rule
                FROM information_schema.table_constraints tc
                JOIN information_schema.referential_constraints rc
                  ON rc.constraint_name = tc.constraint_name AND rc.constraint_schema = tc.table_schema
                JOIN information_schema.constraint_column_usage ccu
                  ON ccu.constraint_name = tc.constraint_name AND ccu.constraint_schema = tc.table_schema
                WHERE tc.constraint_type = 'FOREIGN KEY' AND tc.table_schema = 'public'
                  AND tc.table_name = 'pokemon' AND ccu.table_name = 'users'
                """,
                String.class);

        assertThat(deleteRule).isEqualTo("SET NULL");
    }

    // WU-US03-A E2 says "@Version on both roots"; the ERD gives version to POKEMON alone and
    // the domain User carries none. A curator with no version to send back cannot have one
    // checked, so a column on users would be the appearance of optimistic locking, not the
    // fact of it — and the difference only shows up as a lost update nobody notices.
    @Test
    void should_carry_a_version_column_on_pokemon_alone_among_the_roots() {
        List<String> versioned = jdbc.queryForList(
                """
                SELECT table_name FROM information_schema.columns
                WHERE table_schema = 'public' AND column_name = 'version' AND is_nullable = 'NO'
                ORDER BY table_name
                """,
                String.class);

        assertThat(versioned).containsExactly("pokemon");
    }

    @Test
    void should_store_mass_and_height_in_upstream_units_when_the_column_is_declared() {
        List<String> columns = jdbc.queryForList(
                """
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'pokemon'
                  AND column_name LIKE ANY (ARRAY['mass%', 'height%'])
                ORDER BY column_name
                """,
                String.class);

        assertThat(columns).containsExactly("height_decimetres", "mass_hectograms");
    }
}
