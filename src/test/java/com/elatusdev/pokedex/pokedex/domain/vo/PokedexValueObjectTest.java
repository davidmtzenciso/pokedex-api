package com.elatusdev.pokedex.pokedex.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.elatusdev.pokedex.shared.domain.exception.InvalidPokemonDataException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PokedexValueObjectTest {

    @Nested
    class RegionTest {

        @Test
        void should_parse_case_insensitively() {
            assertThat(Region.fromString("kanto")).isEqualTo(Region.KANTO);
            assertThat(Region.fromString("  HOENN ")).isEqualTo(Region.HOENN);
        }

        @Test
        void should_reject_an_unknown_region_and_name_the_legal_values() {
            assertThatThrownBy(() -> Region.fromString("MIDDLE_EARTH"))
                    .isInstanceOf(InvalidPokemonDataException.class)
                    .hasMessageContaining("KANTO");
        }

        @Test
        void should_reject_null() {
            assertThatThrownBy(() -> Region.fromString(null))
                    .isInstanceOf(InvalidPokemonDataException.class);
        }

        @Test
        void should_render_a_display_name() {
            assertThat(Region.KANTO.display()).isEqualTo("Kanto");
        }
    }

    @Nested
    class TagTest {

        @Test
        void should_compare_case_insensitively() {
            assertThat(new Tag("Starter")).isEqualTo(new Tag("starter"));
            assertThat(new Tag("Starter")).hasSameHashCodeAs(new Tag("STARTER"));
        }

        @Test
        void should_reject_a_tag_over_thirty_characters() {
            assertThatThrownBy(() -> new Tag("x".repeat(31)))
                    .isInstanceOf(InvalidPokemonDataException.class);
        }

        @Test
        void should_not_equal_a_value_of_another_type() {
            assertThat(new Tag("starter")).isNotEqualTo("starter");
        }

        @Test
        void should_accept_a_tag_of_exactly_thirty_characters() {
            assertThat(new Tag("t".repeat(30)).label()).hasSize(30);
        }

        // pinned to the lower-cased value: case-insensitive equality demands a
        // case-insensitive hash, and a constant hash would satisfy equals but not this
        @Test
        void should_hash_case_insensitively() {
            assertThat(new Tag("Starter").hashCode()).isEqualTo("starter".hashCode());
            assertThat(new Tag("STARTER").hashCode()).isEqualTo(new Tag("starter").hashCode());
        }

        @Test
        void should_not_equal_a_tag_with_a_different_label() {
            assertThat(new Tag("starter")).isNotEqualTo(new Tag("legendary"));
        }

        @Test
        void should_reject_a_blank_tag() {
            assertThatThrownBy(() -> new Tag(" ")).isInstanceOf(InvalidPokemonDataException.class);
        }
    }

    @Nested
    class NotesTest {

        @Test
        void should_strip_and_carry_the_value() {
            assertThat(new Notes("  needs a better sprite ").value()).isEqualTo("needs a better sprite");
        }

        @Test
        void should_accept_notes_of_exactly_two_thousand_characters() {
            assertThat(new Notes("n".repeat(2000)).value()).hasSize(2000);
        }

        @Test
        void should_reject_notes_over_two_thousand_characters() {
            assertThatThrownBy(() -> new Notes("n".repeat(2001)))
                    .isInstanceOf(InvalidPokemonDataException.class)
                    .hasMessageContaining("2000");
        }

        @Test
        void should_reject_blank() {
            assertThatThrownBy(() -> new Notes("  ")).isInstanceOf(InvalidPokemonDataException.class);
        }

        @Test
        void should_reject_null() {
            assertThatThrownBy(() -> new Notes(null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class PokemonIdTest {

        @Test
        void should_reject_non_positive_identifiers() {
            assertThatThrownBy(() -> PokemonId.of(0)).isInstanceOf(InvalidPokemonDataException.class);
            assertThatThrownBy(() -> PokemonId.of(-1)).isInstanceOf(InvalidPokemonDataException.class);
        }

        @Test
        void should_carry_the_value() {
            assertThat(PokemonId.of(7).value()).isEqualTo(7L);
        }
    }
}
