package com.elatusdev.pokedex.pokedex.domain;

import com.elatusdev.pokedex.shared.domain.ReplicatedFields;
import java.util.Objects;
import java.util.Set;

// F7 / AC5 — re-synchronisation replaces every replicated field and preserves every
// proprietary one.
//
// The implementation is a pair construction, and that is the point rather than an
// embarrassment: every field belongs to exactly one authority, so
// Proprietary ∩ Replicated = ∅ and there is nothing to reconcile (ADR-0007). A merge with
// conflict resolution in it would mean the partition had failed.
//
// What this class carries that a pair does not is the partition written down. The two sets
// below are checked against the records' actual components by PokemonMergePolicyTest, so
// adding a field to either record without deciding which authority owns it fails the build,
// and adding it to both fails it twice.
//
// Pure by construction: no repository, no clock, no Spring. The caller applies the result
// and owns the state transition, because choosing SYNCED or CUSTOMIZED needs a clock and
// this needs to stay property-testable.
public final class PokemonMergePolicy {

    // authority: PokeAPI
    public static final Set<String> REPLICATED_FIELDS = Set.of(
            "name",
            "category",
            "mass",
            "height",
            "baseExperience",
            "sprite",
            "description",
            "abilities",
            "stats",
            "types",
            "evolutionLinks",
            "upstreamNames");

    // authority: the curator
    public static final Set<String> PROPRIETARY_FIELDS =
            Set.of("region", "notes", "curatedBy", "tags", "curatorNames");

    public record MergedFields(ReplicatedFields replicated, ProprietaryFields proprietary) {

        public MergedFields {
            Objects.requireNonNull(replicated, "replicated");
            Objects.requireNonNull(proprietary, "proprietary");
        }
    }

    public MergedFields merge(Pokemon existing, ReplicatedFields upstream) {
        Objects.requireNonNull(existing, "existing");
        Objects.requireNonNull(upstream, "upstream");
        return new MergedFields(upstream, existing.proprietary());
    }
}
