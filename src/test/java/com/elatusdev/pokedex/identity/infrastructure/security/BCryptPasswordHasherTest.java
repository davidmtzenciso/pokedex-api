package com.elatusdev.pokedex.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.elatusdev.pokedex.identity.domain.vo.PasswordHash;
import org.junit.jupiter.api.Test;

class BCryptPasswordHasherTest {

    private static final String RAW = "Demo123!correct-horse";

    private final BCryptPasswordHasher hasher = new BCryptPasswordHasher();

    // java:S5344 — SHA-256 is a digest, not a password hash. The cost factor is the whole
    // defence against an offline grind, so it is asserted rather than assumed.
    @Test
    void should_hash_with_bcrypt_at_cost_twelve() {
        PasswordHash hash = hasher.hash(RAW);

        assertThat(hash.value()).startsWith("$2a$12$").hasSize(60);
    }

    @Test
    void should_accept_the_password_it_hashed() {
        assertThat(hasher.matches(RAW, hasher.hash(RAW))).isTrue();
    }

    @Test
    void should_reject_a_different_password() {
        assertThat(hasher.matches("Demo123!wrong-horse", hasher.hash(RAW))).isFalse();
    }

    @Test
    void should_reject_a_password_differing_only_in_case() {
        assertThat(hasher.matches(RAW.toUpperCase(java.util.Locale.ROOT), hasher.hash(RAW)))
                .isFalse();
    }

    // a per-hash salt is what stops one rainbow table from covering every user
    @Test
    void should_produce_a_different_hash_each_time_for_the_same_password() {
        PasswordHash first = hasher.hash(RAW);
        PasswordHash second = hasher.hash(RAW);

        assertThat(first.value()).isNotEqualTo(second.value());
        assertThat(hasher.matches(RAW, first)).isTrue();
        assertThat(hasher.matches(RAW, second)).isTrue();
    }

    @Test
    void should_reject_a_hash_that_was_never_a_bcrypt_hash() {
        assertThat(hasher.matches(RAW, new PasswordHash("not-a-bcrypt-hash"))).isFalse();
    }
}
