package com.elatusdev.pokedex.pokedex.domain;

import com.elatusdev.pokedex.shared.domain.PokeApiId;
import com.elatusdev.pokedex.shared.domain.ReplicatedFields;
import java.util.Objects;

// What replication needs from upstream, stated in this context's own terms: an identity and
// the replicated half. Named separately from the catalogue's CatalogPokemon on purpose —
// this context must not import that one, or the two contexts depend on each other and the
// slice graph cycles.
public record UpstreamPokemon(PokeApiId pokeApiId, ReplicatedFields replicated) {

    public UpstreamPokemon {
        Objects.requireNonNull(pokeApiId, "pokeApiId");
        Objects.requireNonNull(replicated, "replicated");
    }
}
