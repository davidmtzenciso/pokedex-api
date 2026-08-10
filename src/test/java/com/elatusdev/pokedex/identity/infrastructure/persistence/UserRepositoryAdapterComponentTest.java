package com.elatusdev.pokedex.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.elatusdev.pokedex.identity.domain.model.Role;
import com.elatusdev.pokedex.identity.domain.model.User;
import com.elatusdev.pokedex.identity.domain.port.UserRepository;
import com.elatusdev.pokedex.identity.domain.vo.Email;
import com.elatusdev.pokedex.identity.domain.vo.PasswordHash;
import com.elatusdev.pokedex.identity.domain.vo.UserId;
import com.elatusdev.pokedex.identity.domain.vo.Username;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class UserRepositoryAdapterComponentTest {

    private final UserRepository repository;
    private final JdbcTemplate jdbc;

    UserRepositoryAdapterComponentTest(@Autowired UserRepository repository, @Autowired JdbcTemplate jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void clearDatabase() {
        PokemonFixture.clear(jdbc);
    }

    @Test
    void should_return_every_field_unchanged_when_a_saved_user_is_read_back() {
        User saved = repository.save(PokemonFixture.curator("ash"));

        User found = repository.findById(saved.id().orElseThrow()).orElseThrow();

        assertThat(found.username()).isEqualTo(new Username("ash"));
        assertThat(found.email()).isEqualTo(new Email("ash@example.com"));
        assertThat(found.passwordHash()).isEqualTo(new PasswordHash("$2a$10$abcdefghijklmnopqrstuv"));
        assertThat(found.roles()).containsExactly(Role.CURATOR);
        assertThat(found.createdAt()).isEqualTo(Instant.parse("2026-08-01T09:00:00Z"));
    }

    // roles round-trip through a single column, so a set of more than one is the case that
    // proves the join and the split agree
    @Test
    void should_round_trip_every_role_when_the_user_holds_more_than_one() {
        User admin = User.register(
                new Username("oak"),
                new Email("oak@example.com"),
                new PasswordHash("$2a$10$oakoakoakoakoakoakoak"),
                Set.of(Role.CURATOR, Role.ADMIN),
                Instant.parse("2026-08-01T09:00:00Z"));

        UserId id = repository.save(admin).id().orElseThrow();

        assertThat(repository.findById(id).orElseThrow().roles()).containsExactlyInAnyOrder(Role.CURATOR, Role.ADMIN);
    }

    // users arrive by hand as well as through the adapter — V2__seed.sql inserts the demo
    // curators as SQL — and a hand-written roles column carries hand-written whitespace and
    // the occasional double comma. The parse tolerates both rather than throwing on a row a
    // human typed.
    @Test
    void should_parse_the_roles_column_when_a_seeded_row_was_written_by_hand() {
        jdbc.update(
                """
                INSERT INTO users (username, email, password_hash, roles, created_at)
                VALUES ('seeded', 'seeded@example.com', '$2a$10$seededseededseeded', ' CURATOR,,ADMIN ', now())
                """);

        User found = repository.findByUsername(new Username("seeded")).orElseThrow();

        assertThat(found.roles()).containsExactlyInAnyOrder(Role.CURATOR, Role.ADMIN);
    }

    @Test
    void should_find_by_username_when_the_user_is_registered() {
        UserId id = repository.save(PokemonFixture.curator("misty")).id().orElseThrow();

        assertThat(repository.findByUsername(new Username("misty")).flatMap(User::id))
                .contains(id);
    }

    // Username normalises to lower case on construction, so the stored value is normalised
    // and plain equality is already the case-insensitive comparison
    @Test
    void should_find_by_username_when_the_case_differs() {
        UserId id = repository.save(PokemonFixture.curator("brock")).id().orElseThrow();

        assertThat(repository.findByUsername(new Username("BROCK")).flatMap(User::id))
                .contains(id);
    }

    @Test
    void should_report_the_user_exists_when_the_username_or_email_is_taken() {
        repository.save(PokemonFixture.curator("ash"));

        assertThat(repository.existsByUsername(new Username("ash"))).isTrue();
        assertThat(repository.existsByEmail(new Email("ash@example.com"))).isTrue();
    }

    @Test
    void should_report_the_user_is_absent_when_nothing_matches() {
        assertThat(repository.findById(UserId.of(404))).isEmpty();
        assertThat(repository.findByUsername(new Username("nobody"))).isEmpty();
        assertThat(repository.existsByUsername(new Username("nobody"))).isFalse();
        assertThat(repository.existsByEmail(new Email("nobody@example.com"))).isFalse();
    }

    @Test
    void should_reject_a_second_user_when_the_username_is_already_taken() {
        repository.save(PokemonFixture.curator("ash"));

        assertThatThrownBy(() -> repository.save(User.register(
                        new Username("ash"),
                        new Email("different@example.com"),
                        new PasswordHash("$2a$10$abcdefghijklmnopqrstuv"),
                        Set.of(Role.CURATOR),
                        Instant.parse("2026-08-02T09:00:00Z"))))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ux_users_username");
    }

    @Test
    void should_reject_a_second_user_when_the_email_is_already_taken() {
        repository.save(PokemonFixture.curator("ash"));

        assertThatThrownBy(() -> repository.save(User.register(
                        new Username("ash2"),
                        new Email("ash@example.com"),
                        new PasswordHash("$2a$10$abcdefghijklmnopqrstuv"),
                        Set.of(Role.CURATOR),
                        Instant.parse("2026-08-02T09:00:00Z"))))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ux_users_email");
    }
}
