package com.elatusdev.pokedex.identity.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.elatusdev.pokedex.identity.domain.RefreshToken;
import com.elatusdev.pokedex.identity.domain.RefreshTokenRepository;
import com.elatusdev.pokedex.identity.domain.User;
import com.elatusdev.pokedex.identity.domain.UserId;
import com.elatusdev.pokedex.identity.domain.UserRepository;
import com.elatusdev.pokedex.testsupport.PokemonFixture;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

// Until now rotation and family revocation were proven against a test-scope fake with no
// runtime implementation behind it: the table existed and nothing reached it. These are the
// things a fake could not check — the unique constraint, the cascade, and that a revocation
// updates a row rather than inserting a second one.
@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class RefreshTokenRepositoryAdapterComponentTest {

    // Postgres stores TIMESTAMPTZ at microsecond precision, so a nanosecond-precision
    // Instant does not survive the round trip. Truncating here asserts the mapping, not the
    // column's resolution.
    private static final Instant EXPIRES_AT =
            Instant.parse("2026-08-17T09:00:00Z").truncatedTo(ChronoUnit.MICROS);
    private static final Instant NOW = Instant.parse("2026-08-10T09:00:00Z").truncatedTo(ChronoUnit.MICROS);

    private final RefreshTokenRepository repository;
    private final UserRepository users;
    private final JdbcTemplate jdbc;

    RefreshTokenRepositoryAdapterComponentTest(
            @Autowired RefreshTokenRepository repository,
            @Autowired UserRepository users,
            @Autowired JdbcTemplate jdbc) {
        this.repository = repository;
        this.users = users;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void clearDatabase() {
        PokemonFixture.clear(jdbc);
    }

    private UserId curator(String username) {
        User saved = users.save(PokemonFixture.curator(username));
        return saved.id().orElseThrow();
    }

    private RefreshToken issued(UserId userId, String familyId, String jti) {
        return RefreshToken.issue(userId, familyId, jti, EXPIRES_AT);
    }

    @Test
    void should_return_every_field_unchanged_when_a_saved_token_is_read_back() {
        UserId userId = curator("ash");

        repository.save(issued(userId, "family-1", "jti-1"));

        RefreshToken found = repository.findByJti("jti-1").orElseThrow();
        assertThat(found.userId()).isEqualTo(userId);
        assertThat(found.familyId()).isEqualTo("family-1");
        assertThat(found.jti()).isEqualTo("jti-1");
        assertThat(found.expiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(found.revokedAt()).isEmpty();
        assertThat(found.isLive(NOW)).isTrue();
    }

    @Test
    void should_assign_an_identity_when_a_token_is_saved_for_the_first_time() {
        UserId userId = curator("ash");

        RefreshToken saved = repository.save(issued(userId, "family-1", "jti-1"));

        assertThat(saved.id()).isPresent();
        assertThat(saved.id().orElseThrow().value()).isPositive();
    }

    @Test
    void should_return_empty_when_no_token_carries_that_jti() {
        assertThat(repository.findByJti("jti-absent")).isEmpty();
    }

    @Test
    void should_return_every_token_in_the_family_and_nothing_from_another() {
        UserId userId = curator("ash");
        repository.save(issued(userId, "family-1", "jti-1"));
        repository.save(issued(userId, "family-1", "jti-2"));
        repository.save(issued(userId, "family-2", "jti-3"));

        List<RefreshToken> family = repository.findByFamilyId("family-1");

        assertThat(family).hasSize(2);
        assertThat(family.stream().map(RefreshToken::jti)).containsExactlyInAnyOrder("jti-1", "jti-2");
    }

    @Test
    void should_return_an_empty_family_when_the_id_matches_nothing() {
        assertThat(repository.findByFamilyId("family-absent")).isEmpty();
    }

    // the rotation path saves the presented token back revoked; that must UPDATE the row,
    // because a second row with the same jti would violate ux_refresh_tokens_jti
    @Test
    void should_update_the_existing_row_when_a_token_is_saved_again_revoked() {
        UserId userId = curator("ash");
        RefreshToken saved = repository.save(issued(userId, "family-1", "jti-1"));

        RefreshToken revoked = repository.save(saved.revoke(NOW));

        assertThat(revoked.id()).isEqualTo(saved.id());
        assertThat(revoked.revokedAt()).contains(NOW);
        assertThat(repository.findByJti("jti-1").orElseThrow().isRevoked()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens", Long.class))
                .isEqualTo(1L);
    }

    // I8/F11 — replaying a rotated token revokes the WHOLE family, synchronously
    @Test
    void should_revoke_every_token_in_the_family_in_one_call() {
        UserId userId = curator("ash");
        repository.save(issued(userId, "family-1", "jti-1"));
        repository.save(issued(userId, "family-1", "jti-2"));

        List<RefreshToken> family = repository.findByFamilyId("family-1");
        repository.saveAll(family.stream().map(token -> token.revoke(NOW)).toList());

        assertThat(repository.findByFamilyId("family-1"))
                .allSatisfy(token -> assertThat(token.isRevoked()).isTrue());
        assertThat(repository.findByFamilyId("family-1"))
                .noneSatisfy(token -> assertThat(token.isLive(NOW)).isTrue());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens", Long.class))
                .isEqualTo(2L);
    }

    @Test
    void should_return_an_empty_list_when_saving_no_tokens() {
        assertThat(repository.saveAll(List.of())).isEmpty();
    }

    // ux_refresh_tokens_jti — a jti is the session identifier, and two rows sharing one
    // would make revocation ambiguous. The fake could not check this.
    @Test
    void should_reject_a_second_token_with_the_same_jti() {
        UserId userId = curator("ash");
        repository.save(issued(userId, "family-1", "jti-1"));

        assertThatThrownBy(() -> repository.save(issued(userId, "family-2", "jti-1")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // fk_refresh_tokens_user ON DELETE CASCADE — deleting a user must not strand its
    // sessions. The fake could not check this either.
    @Test
    void should_remove_the_tokens_when_the_owning_user_is_deleted() {
        UserId userId = curator("ash");
        repository.save(issued(userId, "family-1", "jti-1"));

        jdbc.update("DELETE FROM users WHERE id = ?", userId.value());

        assertThat(repository.findByJti("jti-1")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens", Long.class))
                .isZero();
    }

    @Test
    void should_round_trip_a_revocation_instant_rather_than_only_the_flag() {
        UserId userId = curator("ash");
        RefreshToken saved = repository.save(issued(userId, "family-1", "jti-1"));
        repository.save(saved.revoke(NOW));

        Optional<Instant> revokedAt = repository.findByJti("jti-1").orElseThrow().revokedAt();

        assertThat(revokedAt).contains(NOW);
    }
}
