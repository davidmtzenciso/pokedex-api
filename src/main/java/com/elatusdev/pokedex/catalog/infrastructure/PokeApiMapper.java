package com.elatusdev.pokedex.catalog.infrastructure;

import com.elatusdev.pokedex.shared.domain.InvalidPokemonDataException;
import com.elatusdev.pokedex.shared.domain.EvolutionLink;
import com.elatusdev.pokedex.shared.domain.LocalizedName;
import com.elatusdev.pokedex.shared.domain.NameSource;
import com.elatusdev.pokedex.shared.domain.PokemonAbility;
import com.elatusdev.pokedex.shared.domain.PokemonStat;
import com.elatusdev.pokedex.shared.domain.PokemonType;
import com.elatusdev.pokedex.shared.domain.ReplicatedFields;
import com.elatusdev.pokedex.shared.domain.Category;
import com.elatusdev.pokedex.shared.domain.Description;
import com.elatusdev.pokedex.shared.domain.Height;
import com.elatusdev.pokedex.shared.domain.Mass;
import com.elatusdev.pokedex.shared.domain.PokeApiId;
import com.elatusdev.pokedex.shared.domain.PokemonName;
import com.elatusdev.pokedex.shared.domain.Sprite;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import com.elatusdev.pokedex.catalog.domain.CatalogPokemon;

// The one place that knows PokeAPI is awkward, so nothing downstream has to. Every
// transformation here is a documented IAR entry.
public class PokeApiMapper {

    private static final String ENGLISH = "en";

    public CatalogPokemon toPokemon(
            PokeApiPokemonResponse pokemon, PokeApiSpeciesResponse species, List<EvolutionLink> evolution) {
        return CatalogPokemon.upstream(PokeApiId.of(pokemon.id()), toReplicatedFields(pokemon, species, evolution));
    }

    public ReplicatedFields toReplicatedFields(
            PokeApiPokemonResponse pokemon, PokeApiSpeciesResponse species, List<EvolutionLink> evolution) {
        return new ReplicatedFields(
                new PokemonName(pokemon.name()),
                Optional.of(englishCategory(species)),
                Mass.ofHectograms(pokemon.weight()),
                Height.ofDecimetres(pokemon.height()),
                pokemon.baseExperience() == null ? 0 : pokemon.baseExperience(),
                toSprite(pokemon.sprites()),
                englishDescription(species),
                toAbilities(pokemon.abilities()),
                toStats(pokemon.stats()),
                toTypes(pokemon.types()),
                evolution,
                toLocalizedNames(species.names()));
    }

    // IA2 — genera[0] is Japanese in the live payload. Filtering to English is the whole
    // point; a missing English genus fails loudly rather than shipping the wrong language.
    private static Category englishCategory(PokeApiSpeciesResponse species) {
        return nullSafe(species.genera()).stream()
                .filter(genus -> ENGLISH.equals(genus.language().name()))
                .findFirst()
                .map(genus -> new Category(genus.genus()))
                .orElseThrow(() -> new InvalidPokemonDataException(
                        "species " + species.name() + " carries no English genus"));
    }

    // IA4 — Description strips the literal \n and \f upstream embeds, on construction
    private static Optional<Description> englishDescription(PokeApiSpeciesResponse species) {
        return nullSafe(species.flavorTextEntries()).stream()
                .filter(entry -> ENGLISH.equals(entry.language().name()))
                .findFirst()
                .map(entry -> new Description(entry.flavorText()));
    }

    // IA6 — upstream already ships 12 locales, so the catalogue is useful before a curator
    // has typed anything. Curator overrides are a separate, proprietary set.
    private static List<LocalizedName> toLocalizedNames(List<PokeApiSpeciesResponse.LocalName> names) {
        return nullSafe(names).stream()
                .map(name -> new LocalizedName(name.language().name(), name.name(), NameSource.UPSTREAM))
                .toList();
    }

    private static List<PokemonAbility> toAbilities(List<PokeApiPokemonResponse.AbilityEntry> abilities) {
        return nullSafe(abilities).stream()
                .map(entry -> new PokemonAbility(entry.ability().name(), entry.slot(), entry.hidden()))
                .toList();
    }

    private static List<PokemonStat> toStats(List<PokeApiPokemonResponse.StatEntry> stats) {
        return nullSafe(stats).stream()
                .map(entry -> new PokemonStat(entry.stat().name(), entry.baseStat(), entry.effort()))
                .toList();
    }

    private static List<PokemonType> toTypes(List<PokeApiPokemonResponse.TypeEntry> types) {
        return nullSafe(types).stream()
                .map(entry -> new PokemonType(entry.type().name(), entry.slot()))
                .toList();
    }

    private static Sprite toSprite(PokeApiPokemonResponse.Sprites sprites) {
        if (sprites == null) {
            return Sprite.NONE;
        }
        return new Sprite(toUri(sprites.frontDefault()), officialArtwork(sprites));
    }

    private static URI officialArtwork(PokeApiPokemonResponse.Sprites sprites) {
        return sprites.other() == null || sprites.other().officialArtwork() == null
                ? null
                : toUri(sprites.other().officialArtwork().frontDefault());
    }

    private static URI toUri(String value) {
        return value == null || value.isBlank() ? null : URI.create(value);
    }

    private static <T> List<T> nullSafe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
