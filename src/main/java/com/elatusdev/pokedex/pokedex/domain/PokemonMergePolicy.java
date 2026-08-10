package com.elatusdev.pokedex.pokedex.domain;

import com.elatusdev.pokedex.shared.domain.ReplicatedFields;
import java.time.Instant;
import java.util.Set;

// F7 / I5 / AC5 — the rule that makes replication safe.
//
// There is no conflict resolution here, and that is the design rather than an omission:
// every field belongs to exactly one of two disjoint sets, so re-sync has nothing to
// reconcile (ADR-0007). What this class contributes is the two sets, named, so that the
// disjointness is a thing a test can check rather than a claim in a document.
//
// Pure: no repository, no clock, no Spring. The instant arrives as an argument precisely so
// this stays testable without one.
public final class PokemonMergePolicy {

    // These are the R11 guard. A field added to either record and named in neither constant
    // fails PokemonMergePolicyTest, which reflects over the record components — because the
    // alternative is a new field silently belonging to nobody and being dropped, or
    // overwritten, on the next re-sync. Adding a field here is a deliberate act.
    public static final Set<String> REPLICATED = Set.of(
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

    public static final Set<String> PROPRIETARY = Set.of("region", "notes", "curatedBy", "tags", "curatorNames");

    // void, not Pokemon. Returning the instance it just mutated reads as though a new
    // aggregate came back, and PIT proved the value was dead weight: the mutant that
    // returned null survived every test, because nothing had any reason to look at it.
    public void merge(Pokemon existing, ReplicatedFields upstream, Instant at) {
        existing.replaceReplicated(upstream, at);
    }
}
