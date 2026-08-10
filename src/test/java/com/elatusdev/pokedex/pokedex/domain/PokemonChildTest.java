package com.elatusdev.pokedex.pokedex.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.elatusdev.pokedex.shared.domain.InvalidPokemonDataException;
import com.elatusdev.pokedex.shared.domain.PokeApiId;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.elatusdev.pokedex.shared.domain.PokemonType;
import com.elatusdev.pokedex.shared.domain.PokemonStat;
import com.elatusdev.pokedex.shared.domain.PokemonAbility;
import com.elatusdev.pokedex.shared.domain.NameSource;
import com.elatusdev.pokedex.shared.domain.LocalizedName;
import com.elatusdev.pokedex.shared.domain.EvolutionLink;

class PokemonChildTest {

    @Nested
    class PokemonAbilityTest {

        @Test
        void should_normalise_the_name_and_carry_the_slot() {
            PokemonAbility ability = new PokemonAbility("  Overgrow ", 1, false);

            assertThat(ability.name()).isEqualTo("overgrow");
            assertThat(ability.slot()).isEqualTo(1);
            assertThat(ability.hidden()).isFalse();
        }

        @Test
        void should_reject_a_blank_name() {
            assertThatThrownBy(() -> new PokemonAbility(" ", 1, false))
                    .isInstanceOf(InvalidPokemonDataException.class)
                    .hasMessageContaining("ability name");
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1})
        void should_reject_a_non_positive_slot(int slot) {
            assertThatThrownBy(() -> new PokemonAbility("overgrow", slot, false))
                    .isInstanceOf(InvalidPokemonDataException.class)
                    .hasMessageContaining("slot");
        }
    }

    @Nested
    class PokemonStatTest {

        @Test
        void should_normalise_the_name_and_carry_the_values() {
            PokemonStat stat = new PokemonStat(" Special-Attack ", 65, 1);

            assertThat(stat.name()).isEqualTo("special-attack");
            assertThat(stat.baseValue()).isEqualTo(65);
            assertThat(stat.effort()).isEqualTo(1);
        }

        @Test
        void should_accept_a_zero_effort_because_most_stats_award_none() {
            assertThat(new PokemonStat("speed", 45, 0).effort()).isZero();
        }

        @Test
        void should_reject_a_negative_base_value() {
            assertThatThrownBy(() -> new PokemonStat("speed", -1, 0))
                    .isInstanceOf(InvalidPokemonDataException.class)
                    .hasMessageContaining("baseValue");
        }

        @Test
        void should_reject_a_negative_effort() {
            assertThatThrownBy(() -> new PokemonStat("speed", 45, -1))
                    .isInstanceOf(InvalidPokemonDataException.class)
                    .hasMessageContaining("effort");
        }

        @Test
        void should_reject_a_blank_name() {
            assertThatThrownBy(() -> new PokemonStat(" ", 45, 0))
                    .isInstanceOf(InvalidPokemonDataException.class)
                    .hasMessageContaining("stat name");
        }

        @Test
        void should_accept_a_zero_base_value() {
            assertThat(new PokemonStat("speed", 0, 0).baseValue()).isZero();
        }
    }

    @Nested
    class PokemonTypeTest {

        @Test
        void should_normalise_the_name_and_carry_the_slot() {
            PokemonType type = new PokemonType(" Poison ", 2);

            assertThat(type.name()).isEqualTo("poison");
            assertThat(type.slot()).isEqualTo(2);
        }

        @Test
        void should_reject_a_non_positive_slot() {
            assertThatThrownBy(() -> new PokemonType("poison", 0))
                    .isInstanceOf(InvalidPokemonDataException.class)
                    .hasMessageContaining("slot");
        }

        @Test
        void should_reject_a_blank_name() {
            assertThatThrownBy(() -> new PokemonType(" ", 1))
                    .isInstanceOf(InvalidPokemonDataException.class)
                    .hasMessageContaining("type name");
        }
    }

    @Nested
    class LocalizedNameTest {

        @Test
        void should_normalise_the_locale_and_carry_the_value() {
            LocalizedName name = new LocalizedName(" JA ", " フシギダネ ", NameSource.UPSTREAM);

            assertThat(name.locale()).isEqualTo("ja");
            assertThat(name.value()).isEqualTo("フシギダネ");
            assertThat(name.source()).isEqualTo(NameSource.UPSTREAM);
        }

        @Test
        void should_distinguish_a_curator_override_from_the_seeded_upstream_name() {
            LocalizedName upstream = new LocalizedName("es", "Bulbasaur", NameSource.UPSTREAM);
            LocalizedName curator = new LocalizedName("es", "Bulbasaur", NameSource.CURATOR);

            assertThat(upstream).isNotEqualTo(curator);
        }

        @Test
        void should_reject_a_blank_locale() {
            assertThatThrownBy(() -> new LocalizedName(" ", "Bulbasaur", NameSource.UPSTREAM))
                    .isInstanceOf(InvalidPokemonDataException.class)
                    .hasMessageContaining("locale");
        }

        @Test
        void should_reject_a_blank_value() {
            assertThatThrownBy(() -> new LocalizedName("es", " ", NameSource.UPSTREAM))
                    .isInstanceOf(InvalidPokemonDataException.class)
                    .hasMessageContaining("localized name");
        }

        @Test
        void should_accept_a_value_of_exactly_one_hundred_and_twenty_characters() {
            assertThat(new LocalizedName("es", "n".repeat(120), NameSource.UPSTREAM).value())
                    .hasSize(120);
        }

        @Test
        void should_reject_a_value_over_one_hundred_and_twenty_characters() {
            assertThatThrownBy(() -> new LocalizedName("es", "n".repeat(121), NameSource.UPSTREAM))
                    .isInstanceOf(InvalidPokemonDataException.class)
                    .hasMessageContaining("localized name");
        }

        @Test
        void should_reject_a_missing_source() {
            assertThatThrownBy(() -> new LocalizedName("es", "Bulbasaur", null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class EvolutionLinkTest {

        @Test
        void should_carry_the_edge_and_its_trigger() {
            EvolutionLink link =
                    new EvolutionLink(PokeApiId.of(1), PokeApiId.of(2), " Level-Up ", Optional.of(16));

            assertThat(link.from()).isEqualTo(PokeApiId.of(1));
            assertThat(link.to()).isEqualTo(PokeApiId.of(2));
            assertThat(link.trigger()).isEqualTo("level-up");
            assertThat(link.minLevel()).contains(16);
        }

        @Test
        void should_carry_no_minimum_level_when_the_trigger_is_not_level_based() {
            EvolutionLink link =
                    new EvolutionLink(PokeApiId.of(133), PokeApiId.of(134), "use-item", Optional.empty());

            assertThat(link.minLevel()).isEmpty();
        }

        @Test
        void should_reject_an_edge_from_a_species_to_itself() {
            assertThatThrownBy(() ->
                            new EvolutionLink(PokeApiId.of(1), PokeApiId.of(1), "level-up", Optional.empty()))
                    .isInstanceOf(InvalidPokemonDataException.class)
                    .hasMessageContaining("itself");
        }

        @Test
        void should_reject_a_blank_trigger() {
            assertThatThrownBy(() ->
                            new EvolutionLink(PokeApiId.of(1), PokeApiId.of(2), " ", Optional.empty()))
                    .isInstanceOf(InvalidPokemonDataException.class)
                    .hasMessageContaining("trigger");
        }

        @Test
        void should_reject_a_non_positive_minimum_level() {
            assertThatThrownBy(() ->
                            new EvolutionLink(PokeApiId.of(1), PokeApiId.of(2), "level-up", Optional.of(0)))
                    .isInstanceOf(InvalidPokemonDataException.class)
                    .hasMessageContaining("minLevel");
        }
    }
}
