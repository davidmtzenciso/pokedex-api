package com.elatusdev.pokedex.catalog.infrastructure.pokeapi;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record PokeApiPokemonResponse(
        int id,
        String name,
        // IA3: hectograms and decimetres, both integers
        int weight,
        int height,
        @JsonProperty("base_experience") Integer baseExperience,
        List<AbilityEntry> abilities,
        List<StatEntry> stats,
        List<TypeEntry> types,
        Sprites sprites,
        PokeApiNameRef species) {

    public record AbilityEntry(PokeApiNameRef ability, @JsonProperty("is_hidden") boolean hidden, int slot) {}

    public record StatEntry(@JsonProperty("base_stat") int baseStat, int effort, PokeApiNameRef stat) {}

    public record TypeEntry(int slot, PokeApiNameRef type) {}

    public record Sprites(@JsonProperty("front_default") String frontDefault, Other other) {}

    public record Other(@JsonProperty("official-artwork") OfficialArtwork officialArtwork) {}

    public record OfficialArtwork(@JsonProperty("front_default") String frontDefault) {}
}
