package com.elatusdev.pokedex.pokedex.application;

import com.elatusdev.pokedex.pokedex.domain.Pokemon;
import com.elatusdev.pokedex.pokedex.domain.ReplicationState;
import com.elatusdev.pokedex.shared.domain.Category;
import com.elatusdev.pokedex.shared.domain.Description;
import com.elatusdev.pokedex.shared.domain.Height;
import com.elatusdev.pokedex.shared.domain.LocalizedName;
import com.elatusdev.pokedex.shared.domain.Mass;
import com.elatusdev.pokedex.shared.domain.NameSource;
import com.elatusdev.pokedex.shared.domain.PokeApiId;
import com.elatusdev.pokedex.shared.domain.PokemonAbility;
import com.elatusdev.pokedex.shared.domain.PokemonName;
import com.elatusdev.pokedex.shared.domain.PokemonStat;
import com.elatusdev.pokedex.shared.domain.PokemonType;
import com.elatusdev.pokedex.shared.domain.ReplicatedFields;
import com.elatusdev.pokedex.shared.domain.Sprite;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

final class SyncFixture {

    private SyncFixture() {
    }

    static Pokemon synced() {
        Pokemon pokemon = Pokemon.pending(PokeApiId.of(1), replicated());
        pokemon.transitionTo(ReplicationState.SYNCED, Instant.parse("2026-08-10T18:00:00Z"));
        return pokemon;
    }

    static ReplicatedFields replicated() {
        return new ReplicatedFields(
                new PokemonName("bulbasaur"),
                Optional.of(new Category("Seed Pokémon")),
                Mass.ofHectograms(69),
                Height.ofDecimetres(7),
                64,
                new Sprite(URI.create("https://img.example/1.png"), URI.create("https://img.example/1-art.png")),
                Optional.of(new Description("A strange seed was planted on its back at birth.")),
                List.of(new PokemonAbility("overgrow", 1, false)),
                List.of(new PokemonStat("hp", 45, 0)),
                List.of(new PokemonType("grass", 1)),
                List.of(),
                List.of(new LocalizedName("ja", "フシギダネ", NameSource.UPSTREAM)));
    }
}
