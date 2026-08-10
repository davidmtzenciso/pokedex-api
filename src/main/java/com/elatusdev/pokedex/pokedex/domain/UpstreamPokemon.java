package com.elatusdev.pokedex.pokedex.domain;

import com.elatusdev.pokedex.shared.domain.PokeApiId;
import com.elatusdev.pokedex.shared.domain.ReplicatedFields;
import java.util.Objects;

// A carrier for UpstreamCatalog, permitted in a port package by N9 because a port sometimes
// needs a type for its own return value.
//
// pokeApiId is required here even though the catalogue's own read model makes it optional:
// a record fetched from upstream by definition has an upstream identity, and replication
// cannot create a local row without one (F6 — DRAFT is exactly the set of unlinked records).
// Syncing by name is the case that needs it, since the caller supplied no id to reuse.
public record UpstreamPokemon(PokeApiId pokeApiId, ReplicatedFields replicated) {

    public UpstreamPokemon {
        Objects.requireNonNull(pokeApiId, "pokeApiId");
        Objects.requireNonNull(replicated, "replicated");
    }
}
