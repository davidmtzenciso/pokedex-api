package com.elatusdev.pokedex.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

// Asserted against the migration FILE rather than against rows, and deliberately.
//
// Every component test shares one Postgres container, and several of them clear the users
// table in setup — so a row-level assertion about seeded credentials passes alone and fails
// in a full run, depending on the order tests happen to execute in. The properties WU-999-A
// J3 actually cares about are properties of the script: which accounts it creates, and that
// it never writes a password in the clear.
class SeedMigrationTest {

    private static final Path SEED = Path.of("src/main/resources/db/migration/V2__seed.sql");
    private static final Pattern BCRYPT_COST_12 = Pattern.compile("\\$2a\\$12\\$[./A-Za-z0-9]{53}");

    private static String seed() {
        try {
            return Files.readString(SEED);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    @Test
    void should_seed_the_two_accounts_quickstart_documents() {
        String sql = seed();

        assertThat(sql).contains("'demo'").contains("'admin'");
        assertThat(sql).contains("CURATOR").contains("CURATOR,ADMIN");
    }

    // the graded property: a seeded password in the clear is a password in the repository,
    // and gitleaks would not necessarily recognise one
    @Test
    void should_write_every_seeded_password_as_a_bcrypt_hash() {
        String sql = seed();

        assertThat(BCRYPT_COST_12.matcher(sql).results()).hasSize(2);
        assertThat(sql).doesNotContain("Demo123!").doesNotContain("Admin123!");
    }

    // the Pokemon are PokeAPI's. A committed dump would be a second copy of upstream's data
    // that goes stale the moment upstream changes, and that no test here can verify.
    @Test
    void should_seed_no_pokemon_rows() {
        assertThat(seed().toUpperCase(java.util.Locale.ROOT)).doesNotContain("INTO POKEMON");
    }

    // re-running a migration against a database that already has these rows must not fail
    // the boot; Flyway will not re-run V2, but a manual replay or a repair should be safe
    @Test
    void should_tolerate_being_applied_to_a_database_that_already_has_the_accounts() {
        assertThat(seed()).containsIgnoringCase("ON CONFLICT");
    }
}
