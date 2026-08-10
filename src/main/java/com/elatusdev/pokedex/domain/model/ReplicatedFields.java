package com.elatusdev.pokedex.domain.model;

import com.elatusdev.pokedex.domain.exception.InvalidPokemonDataException;
import com.elatusdev.pokedex.domain.vo.Category;
import com.elatusdev.pokedex.domain.vo.Description;
import com.elatusdev.pokedex.domain.vo.Height;
import com.elatusdev.pokedex.domain.vo.Mass;
import com.elatusdev.pokedex.domain.vo.PokemonName;
import com.elatusdev.pokedex.domain.vo.Sprite;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

// The Replicated half of the partition in WF-000 §4.7. PokeAPI is the authority for every
// field here, so re-sync overwrites all of them wholesale — see ADR-0007.
public record ReplicatedFields(
        PokemonName name,
        Optional<Category> category,
        Mass mass,
        Height height,
        int baseExperience,
        Sprite sprite,
        Optional<Description> description,
        List<PokemonAbility> abilities,
        List<PokemonStat> stats,
        List<PokemonType> types,
        List<EvolutionLink> evolutionLinks,
        List<LocalizedName> upstreamNames) {

    public ReplicatedFields {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(mass, "mass");
        Objects.requireNonNull(height, "height");
        Objects.requireNonNull(sprite, "sprite");
        Objects.requireNonNull(description, "description");
        abilities = List.copyOf(abilities);
        stats = List.copyOf(stats);
        types = List.copyOf(types);
        evolutionLinks = List.copyOf(evolutionLinks);
        upstreamNames = List.copyOf(upstreamNames);
        if (baseExperience < 0) {
            throw new InvalidPokemonDataException("baseExperience must not be negative, was " + baseExperience);
        }
        requireAllUpstream(upstreamNames);
    }

    private static void requireAllUpstream(List<LocalizedName> names) {
        if (names.stream().anyMatch(localized -> localized.source() != NameSource.UPSTREAM)) {
            throw new InvalidPokemonDataException("every replicated localized name must have source UPSTREAM");
        }
    }
}
