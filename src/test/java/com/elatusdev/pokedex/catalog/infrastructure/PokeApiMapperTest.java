package com.elatusdev.pokedex.catalog.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.elatusdev.pokedex.shared.domain.InvalidPokemonDataException;
import com.elatusdev.pokedex.shared.domain.NameSource;
import com.elatusdev.pokedex.pokedex.domain.Pokemon;
import com.elatusdev.pokedex.shared.domain.PokemonAbility;
import com.elatusdev.pokedex.shared.domain.PokemonType;
import com.elatusdev.pokedex.pokedex.domain.ReplicationState;
import com.elatusdev.pokedex.shared.domain.Category;
import com.elatusdev.pokedex.shared.domain.PokeApiId;
import com.elatusdev.pokedex.shared.domain.PokemonName;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.elatusdev.pokedex.testsupport.PokeApiFixtures;

class PokeApiMapperTest {

    private final PokeApiMapper mapper = new PokeApiMapper();

    private Pokemon bulbasaur() {
        return mapper.toPokemon(PokeApiFixtures.pokemon1(), PokeApiFixtures.species1(), List.of());
    }

    @Test
    void should_map_the_upstream_id_and_name_into_a_pending_aggregate() {
        Pokemon pokemon = bulbasaur();

        assertThat(pokemon.pokeApiId()).contains(PokeApiId.of(1));
        assertThat(pokemon.replicated().name()).isEqualTo(new PokemonName("bulbasaur"));
        assertThat(pokemon.replicationState()).isEqualTo(ReplicationState.PENDING);
    }

    // IA3 — upstream reports hectograms and decimetres. Getting this wrong makes Bulbasaur
    // weigh 69 kg, which is the canonical symptom in the gotchas table.
    @Test
    void should_convert_mass_and_height_from_upstream_units() {
        Pokemon pokemon = bulbasaur();

        assertThat(pokemon.replicated().mass().hectograms()).isEqualTo(69);
        assertThat(pokemon.replicated().mass().toKilograms()).isEqualByComparingTo("6.9");
        assertThat(pokemon.replicated().height().decimetres()).isEqualTo(7);
        assertThat(pokemon.replicated().height().toMetres()).isEqualByComparingTo("0.7");
    }

    // IA2 — genera[0] is Japanese in the recorded payload, so taking the first entry
    // silently ships the wrong language rather than failing
    @Test
    void should_take_the_english_genus_as_the_category_and_never_the_first_entry() {
        Pokemon pokemon = bulbasaur();

        assertThat(PokeApiFixtures.species1().genera().get(0).language().name()).isEqualTo("ja-hrkt");
        assertThat(pokemon.replicated().category()).contains(new Category("Seed Pokémon"));
    }

    @Test
    void should_fail_loudly_when_the_species_carries_no_english_genus() {
        PokeApiSpeciesResponse japaneseOnly = new PokeApiSpeciesResponse(
                1,
                "bulbasaur",
                List.of(new PokeApiSpeciesResponse.Genus(
                        "たねポケモン", new PokeApiNameRef("ja-hrkt", "https://pokeapi.co/api/v2/language/1/"))),
                List.of(),
                List.of(),
                new PokeApiNameRef(null, "https://pokeapi.co/api/v2/evolution-chain/1/"));

        assertThatThrownBy(() -> mapper.toPokemon(PokeApiFixtures.pokemon1(), japaneseOnly, List.of()))
                .isInstanceOf(InvalidPokemonDataException.class)
                .hasMessageContaining("English");
    }

    // IA4 — flavor_text carries literal newlines and form feeds
    @Test
    void should_strip_the_control_characters_upstream_embeds_in_the_description() {
        String raw = PokeApiFixtures.species1().flavorTextEntries().get(0).flavorText();
        assertThat(raw).contains("\n").contains("\f");

        Pokemon pokemon = bulbasaur();

        assertThat(pokemon.replicated().description())
                .isPresent()
                .get()
                .satisfies(description -> assertThat(description.value())
                        .doesNotContain("\n")
                        .doesNotContain("\f")
                        .startsWith("A strange seed was planted"));
    }

    // IA6 — species.names[] already carries 12 locales, so the catalogue ships useful
    @Test
    void should_seed_every_localized_name_from_upstream_with_source_upstream() {
        Pokemon pokemon = bulbasaur();

        assertThat(pokemon.replicated().upstreamNames()).hasSize(12);
        assertThat(pokemon.replicated().upstreamNames())
                .allSatisfy(name -> assertThat(name.source()).isEqualTo(NameSource.UPSTREAM));
        assertThat(pokemon.replicated().upstreamNames())
                .filteredOn(name -> "ja-hrkt".equals(name.locale()))
                .singleElement()
                .satisfies(name -> assertThat(name.value()).isEqualTo("フシギダネ"));
    }

    @Test
    void should_map_abilities_with_their_slot_and_hidden_flag() {
        Pokemon pokemon = bulbasaur();

        assertThat(pokemon.replicated().abilities())
                .contains(new PokemonAbility("overgrow", 1, false))
                .contains(new PokemonAbility("chlorophyll", 3, true));
    }

    @Test
    void should_map_stats_with_their_base_value_and_effort() {
        Pokemon pokemon = bulbasaur();

        assertThat(pokemon.replicated().stats())
                .filteredOn(stat -> "hp".equals(stat.name()))
                .singleElement()
                .satisfies(stat -> {
                    assertThat(stat.baseValue()).isEqualTo(45);
                    assertThat(stat.effort()).isZero();
                });
    }

    // A2 — all six stats, not just the ones the list row happens to show
    @Test
    void should_map_every_one_of_the_six_core_statistics() {
        assertThat(bulbasaur().replicated().stats()).hasSize(6);
        assertThat(bulbasaur().replicated().stats().stream().map(stat -> stat.name()))
                .containsExactlyInAnyOrder(
                        "hp", "attack", "defense", "special-attack", "special-defense", "speed");
    }

    @Test
    void should_map_types_with_their_slot() {
        Pokemon pokemon = bulbasaur();

        assertThat(pokemon.replicated().types())
                .containsExactly(new PokemonType("grass", 1), new PokemonType("poison", 2));
    }

    @Test
    void should_prefer_the_official_artwork_for_the_sprite() {
        Pokemon pokemon = bulbasaur();

        assertThat(pokemon.replicated().sprite().preferred())
                .map(java.net.URI::toString)
                .contains("https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/1.png");
    }

    @Test
    void should_carry_the_base_experience() {
        assertThat(bulbasaur().replicated().baseExperience()).isEqualTo(64);
    }

    @Test
    void should_attach_the_evolution_links_it_is_given() {
        Pokemon pokemon = mapper.toPokemon(
                PokeApiFixtures.pokemon1(),
                PokeApiFixtures.species1(),
                new EvolutionChainMapper().flatten(PokeApiFixtures.evolutionChain1()));

        assertThat(pokemon.replicated().evolutionLinks()).hasSize(2);
    }
}
