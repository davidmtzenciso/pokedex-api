// Copyright (c) 2026 ElatusDev
package com.elatusdev.pokedex.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.elatusdev.pokedex.domain.exception.InvalidPokemonDataException;
import com.elatusdev.pokedex.domain.vo.Email;
import com.elatusdev.pokedex.domain.vo.PasswordHash;
import com.elatusdev.pokedex.domain.vo.UserId;
import com.elatusdev.pokedex.domain.vo.Username;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UserTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-09T14:22:31Z");
    private static final PasswordHash HASH = new PasswordHash("$2a$10$abcdefghijklmnopqrstuv");

    private static User curator() {
        return User.register(
                new Username("demo"), new Email("demo@elatus-dev.com"), HASH, Set.of(Role.CURATOR), CREATED_AT);
    }

    @Test
    void should_carry_its_credentials_and_creation_instant_when_registered() {
        User user = curator();

        assertThat(user.id()).isEmpty();
        assertThat(user.username()).isEqualTo(new Username("demo"));
        assertThat(user.email()).isEqualTo(new Email("demo@elatus-dev.com"));
        assertThat(user.passwordHash()).isEqualTo(HASH);
        assertThat(user.roles()).containsExactly(Role.CURATOR);
        assertThat(user.createdAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void should_carry_its_identity_when_rehydrated() {
        User user = User.rehydrate(
                UserId.of(7),
                new Username("admin"),
                new Email("admin@elatus-dev.com"),
                HASH,
                Set.of(Role.CURATOR, Role.ADMIN),
                CREATED_AT);

        assertThat(user.id()).contains(UserId.of(7));
        assertThat(user.roles()).containsExactlyInAnyOrder(Role.CURATOR, Role.ADMIN);
    }

    @Test
    void should_reject_a_user_with_no_role() {
        assertThatThrownBy(() -> User.register(
                        new Username("demo"), new Email("demo@elatus-dev.com"), HASH, Set.of(), CREATED_AT))
                .isInstanceOf(InvalidPokemonDataException.class)
                .hasMessageContaining("at least one role");
    }

    @Test
    void should_reject_a_user_with_no_password_hash() {
        assertThatThrownBy(() -> User.register(
                        new Username("demo"),
                        new Email("demo@elatus-dev.com"),
                        null,
                        Set.of(Role.CURATOR),
                        CREATED_AT))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void should_report_whether_it_holds_a_role() {
        User user = curator();

        assertThat(user.hasRole(Role.CURATOR)).isTrue();
        assertThat(user.hasRole(Role.ADMIN)).isFalse();
    }

    @Test
    void should_return_an_unmodifiable_view_of_the_roles() {
        Set<Role> roles = curator().roles();

        assertThatThrownBy(() -> roles.add(Role.ADMIN)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void should_not_be_affected_when_the_caller_mutates_the_set_it_passed_in() {
        Set<Role> mutable = new HashSet<>(Set.of(Role.CURATOR));
        User user = User.register(new Username("demo"), new Email("demo@elatus-dev.com"), HASH, mutable, CREATED_AT);

        mutable.add(Role.ADMIN);

        assertThat(user.roles()).containsExactly(Role.CURATOR);
    }

    // I10 — a hash that reaches a log line is a hash an attacker can grind offline
    @Test
    void should_never_expose_the_password_hash_in_toString() {
        assertThat(curator().toString())
                .doesNotContain("$2a$10$abcdefghijklmnopqrstuv")
                .contains("demo");
    }

    @Test
    void should_not_expose_the_password_hash_through_the_optional_identity() {
        assertThat(Optional.of(curator()).toString()).doesNotContain("$2a$10$abcdefghijklmnopqrstuv");
    }
}
