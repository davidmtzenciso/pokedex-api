package com.elatusdev.pokedex.shared.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.elatusdev.pokedex.shared.domain.exception.InvalidPokemonDataException;
import java.net.URI;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SharedValueObjectTest {

    @Nested
    class MassTest {

        @Test
        void should_convert_hectograms_to_kilograms() {
            assertThat(Mass.ofHectograms(69).toKilograms()).isEqualByComparingTo("6.9");
        }

        @Test
        void should_convert_a_heavy_pokemon() {
            assertThat(Mass.ofHectograms(9500).toKilograms()).isEqualByComparingTo("950.0");
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1, -69})
        void should_reject_non_positive_mass(int hectograms) {
            assertThatThrownBy(() -> Mass.ofHectograms(hectograms))
                    .isInstanceOf(InvalidPokemonDataException.class)
                    .hasMessageContaining("must be positive");
        }
    }

    @Nested
    class HeightTest {

        @Test
        void should_convert_decimetres_to_metres() {
            assertThat(Height.ofDecimetres(7).toMetres()).isEqualByComparingTo("0.7");
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -7})
        void should_reject_non_positive_height(int decimetres) {
            assertThatThrownBy(() -> Height.ofDecimetres(decimetres))
                    .isInstanceOf(InvalidPokemonDataException.class);
        }
    }

    @Nested
    class PokemonNameTest {

        @Test
        void should_strip_surrounding_whitespace() {
            assertThat(new PokemonName("  bulbasaur  ").value()).isEqualTo("bulbasaur");
        }

        @Test
        void should_compare_case_insensitively() {
            assertThat(new PokemonName("Bulbasaur")).isEqualTo(new PokemonName("bulbasaur"));
            assertThat(new PokemonName("Bulbasaur")).hasSameHashCodeAs(new PokemonName("BULBASAUR"));
        }

        @Test
        void should_not_equal_a_value_of_another_type() {
            assertThat(new PokemonName("bulbasaur")).isNotEqualTo("bulbasaur");
        }

        @Test
        void should_hash_case_insensitively() {
            assertThat(new PokemonName("Bulbasaur").hashCode()).isEqualTo("bulbasaur".hashCode());
            assertThat(new PokemonName("BULBASAUR").hashCode())
                    .isEqualTo(new PokemonName("bulbasaur").hashCode());
        }

        @Test
        void should_not_equal_a_different_name() {
            assertThat(new PokemonName("bulbasaur")).isNotEqualTo(new PokemonName("ivysaur"));
        }

        @Test
        void should_reject_a_blank_name() {
            assertThatThrownBy(() -> new PokemonName("   "))
                    .isInstanceOf(InvalidPokemonDataException.class)
                    .hasMessageContaining("1..60");
        }

        @Test
        void should_reject_a_name_over_sixty_characters() {
            assertThatThrownBy(() -> new PokemonName("x".repeat(61)))
                    .isInstanceOf(InvalidPokemonDataException.class);
        }

        @Test
        void should_accept_a_name_of_exactly_sixty_characters() {
            assertThat(new PokemonName("x".repeat(60)).value()).hasSize(60);
        }

        @Test
        void should_reject_null() {
            assertThatThrownBy(() -> new PokemonName(null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class DescriptionTest {

        @Test
        void should_strip_newlines_and_form_feeds() {
            var raw = "A strange seed was\nplanted on its\nback at birth.\fThe plant sprouts.";
            assertThat(new Description(raw).value())
                    .doesNotContain("\n")
                    .doesNotContain("\f")
                    .isEqualTo("A strange seed was planted on its back at birth. The plant sprouts.");
        }

        @Test
        void should_collapse_runs_of_whitespace() {
            assertThat(new Description("a\n\n\nb").value()).isEqualTo("a b");
        }

        @Test
        void should_accept_a_description_of_exactly_two_thousand_characters() {
            assertThat(new Description("d".repeat(2000)).value()).hasSize(2000);
        }

        @Test
        void should_reject_a_description_over_two_thousand_characters() {
            assertThatThrownBy(() -> new Description("d".repeat(2001)))
                    .isInstanceOf(InvalidPokemonDataException.class);
        }

        @Test
        void should_reject_a_description_that_normalises_to_empty() {
            assertThatThrownBy(() -> new Description("\n\f\t"))
                    .isInstanceOf(InvalidPokemonDataException.class);
        }
    }

    @Nested
    class SpriteTest {

        @Test
        void should_prefer_official_artwork_when_present() {
            var artwork = URI.create("https://img/official.png");
            var front = URI.create("https://img/front.png");
            assertThat(new Sprite(front, artwork).preferred()).contains(artwork);
        }

        @Test
        void should_fall_back_to_front_default() {
            var front = URI.create("https://img/front.png");
            assertThat(new Sprite(front, null).preferred()).contains(front);
        }

        @Test
        void should_be_empty_when_neither_is_present() {
            assertThat(Sprite.NONE.preferred()).isEmpty();
        }

        @Test
        void should_expose_its_components() {
            var front = URI.create("https://img/front.png");
            assertThat(new Sprite(front, null).frontDefault()).isEqualTo(front);
            assertThat(new Sprite(front, null).officialArtwork()).isNull();
        }
    }

    @Nested
    class CategoryTest {

        @Test
        void should_strip_and_carry_the_value() {
            assertThat(new Category("  Seed Pokemon ").value()).isEqualTo("Seed Pokemon");
        }

        @Test
        void should_reject_blank() {
            assertThatThrownBy(() -> new Category(" ")).isInstanceOf(InvalidPokemonDataException.class);
        }

        @Test
        void should_accept_a_category_of_exactly_sixty_characters() {
            assertThat(new Category("c".repeat(60)).value()).hasSize(60);
        }

        @Test
        void should_reject_a_category_over_sixty_characters() {
            assertThatThrownBy(() -> new Category("c".repeat(61)))
                    .isInstanceOf(InvalidPokemonDataException.class);
        }
    }

    @Nested
    class PokeApiIdTest {

        @Test
        void should_reject_non_positive_identifiers() {
            assertThatThrownBy(() -> PokeApiId.of(0)).isInstanceOf(InvalidPokemonDataException.class);
            assertThatThrownBy(() -> PokeApiId.of(-1)).isInstanceOf(InvalidPokemonDataException.class);
        }

        @Test
        void should_carry_the_value() {
            assertThat(PokeApiId.of(25).value()).isEqualTo(25);
        }
    }
}
