package com.elatusdev.pokedex.identity.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.elatusdev.pokedex.shared.domain.exception.InvalidPokemonDataException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IdentityValueObjectTest {

    @Nested
    class EmailTest {

        @Test
        void should_normalise_to_lowercase() {
            assertThat(new Email("  Demo@Example.COM ").value()).isEqualTo("demo@example.com");
        }

        @ParameterizedTest
        @ValueSource(strings = {"nope", "a@b", "a b@c.com", "@example.com", "a@@b.com"})
        void should_reject_a_malformed_address(String raw) {
            assertThatThrownBy(() -> new Email(raw)).isInstanceOf(InvalidPokemonDataException.class);
        }
    }

    @Nested
    class UsernameTest {

        @Test
        void should_normalise_to_lowercase() {
            assertThat(new Username("Demo_User").value()).isEqualTo("demo_user");
        }

        @ParameterizedTest
        @ValueSource(strings = {"ab", "-nope", "has space", "Ünicode"})
        void should_reject_a_malformed_username(String raw) {
            assertThatThrownBy(() -> new Username(raw)).isInstanceOf(InvalidPokemonDataException.class);
        }

        @Test
        void should_reject_a_username_over_thirty_characters() {
            assertThatThrownBy(() -> new Username("a".repeat(31)))
                    .isInstanceOf(InvalidPokemonDataException.class);
        }
    }

    @Nested
    class PasswordHashTest {

        @Test
        void should_never_expose_the_hash_in_toString() {
            var hash = new PasswordHash("$2a$12$abcdefghijklmnopqrstuv");
            assertThat(hash.toString()).isEqualTo("***").doesNotContain("$2a$12$");
            assertThat("password=" + hash).isEqualTo("password=***");
        }

        @Test
        void should_reject_a_blank_hash() {
            assertThatThrownBy(() -> new PasswordHash(" "))
                    .isInstanceOf(InvalidPokemonDataException.class);
        }
    }

    @Nested
    class UserIdTest {

        @Test
        void should_reject_non_positive_identifiers() {
            assertThatThrownBy(() -> UserId.of(0)).isInstanceOf(InvalidPokemonDataException.class);
            assertThatThrownBy(() -> UserId.of(-1)).isInstanceOf(InvalidPokemonDataException.class);
        }

        @Test
        void should_carry_the_value() {
            assertThat(UserId.of(7).value()).isEqualTo(7L));
        }
    }
}
