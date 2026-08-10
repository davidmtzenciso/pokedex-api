package com.elatusdev.pokedex.pokedex.application;

import com.elatusdev.pokedex.pokedex.domain.Notes;
import com.elatusdev.pokedex.pokedex.domain.Region;
import com.elatusdev.pokedex.pokedex.domain.Tag;
import com.elatusdev.pokedex.shared.domain.Category;
import com.elatusdev.pokedex.shared.domain.Description;
import com.elatusdev.pokedex.shared.domain.Height;
import com.elatusdev.pokedex.shared.domain.Mass;
import com.elatusdev.pokedex.shared.domain.PokeApiId;
import com.elatusdev.pokedex.shared.domain.PokemonName;
import java.util.List;
import java.util.Objects;

public record CreateLocalPokemonCommand(
        PokemonName name,
        java.util.Optional<PokeApiId> pokeApiId,
        Mass mass,
        Height height,
        java.util.Optional<Category> category,
        java.util.Optional<Description> description,
        java.util.Optional<Region> region,
        java.util.Optional<Notes> notes,
        List<Tag> tags) {

    public CreateLocalPokemonCommand {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(pokeApiId, "pokeApiId");
        Objects.requireNonNull(mass, "mass");
        Objects.requireNonNull(height, "height");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(notes, "notes");
        tags = List.copyOf(Objects.requireNonNull(tags, "tags"));
    }
}
