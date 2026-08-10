package com.elatusdev.pokedex.infrastructure.pokeapi;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

// IA2: the category lives here, not on /pokemon/{id}, which is what makes a page cost
// 1 + 2N rather than 1 + N
public record PokeApiSpeciesResponse(
        int id,
        String name,
        List<Genus> genera,
        @JsonProperty("flavor_text_entries") List<FlavorText> flavorTextEntries,
        List<LocalName> names,
        @JsonProperty("evolution_chain") PokeApiNameRef evolutionChain) {

    public record Genus(String genus, PokeApiNameRef language) {}

    public record FlavorText(@JsonProperty("flavor_text") String flavorText, PokeApiNameRef language) {}

    public record LocalName(PokeApiNameRef language, String name) {}
}
