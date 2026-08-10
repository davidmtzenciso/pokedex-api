package com.elatusdev.pokedex.catalog.domain;

import com.elatusdev.pokedex.shared.domain.PokeApiId;
import com.elatusdev.pokedex.shared.domain.ReplicatedFields;
import java.util.Objects;
import java.util.Optional;

// What the catalogue serves: the replicated half of a Pokemon and nothing else. The read
// path is anonymous (BC2), so curation state — region, notes, tags, replication state, the
// local id — is not merely unnecessary here, it is data this context has no business
// returning. Reading through the pokedex aggregate handed all of it out by accident.
//
// pokeApiId is optional because a curator can create a local record that upstream has
// never heard of; such a record has no upstream identity to report.
public record CatalogPokemon(Optional<PokeApiId> pokeApiId, ReplicatedFields replicated) {

    public CatalogPokemon {
        Objects.requireNonNull(pokeApiId, "pokeApiId");
        Objects.requireNonNull(replicated, "replicated");
    }

    public static CatalogPokemon upstream(PokeApiId pokeApiId, ReplicatedFields replicated) {
        return new CatalogPokemon(Optional.of(pokeApiId), replicated);
    }
}
