# ADR-0007: Re-Synchronisation Merges — Proprietary Fields Are Never Overwritten

**Status**: Accepted
**Date**: 2026-08-09
**Deciders**: David Martinez

## Context

User Story 03 asks for local replication whose purpose is "to facilitate the addition of proprietary fields — localized nomenclature, geographical metadata, or internal classification tags." User Story 04 then asks for update operations on those local records.

Put together, these create a conflict the exercise does not mention but any real implementation must answer: **what happens when a record that a curator has edited is re-synchronised from upstream?**

The naive implementations both lose. Overwrite-from-upstream silently destroys curator work — the only data in the system that cannot be re-fetched. Block-re-sync-once-customised freezes a record at whatever upstream said the day it was first edited, so a corrected sprite URL never arrives.

## Decision

Partition every field of the `Pokemon` aggregate into exactly one of two disjoint sets:

- `Replicated ≡ {name, category, mass, height, baseExperience, spriteUrl, description, abilities, stats, types, evolutionLinks, localizedNames[source = UPSTREAM]}`
- `Proprietary ≡ {region, notes, tags, localizedNames[source = CURATOR], curatedBy}`

`Proprietary ∩ Replicated = ∅` **by construction**. Because the partition is total and disjoint, re-sync needs no conflict-resolution policy at all — every field has exactly one authoritative source:

```
reSync(p, upstream) ⟹ p'.Replicated = upstream.Replicated
                    ∧ p'.Proprietary = p.Proprietary
```

This is formal constraint **F7** in the workflow, invariant **I5**, and acceptance criterion **AC5**. It is enforced by `PokemonMergePolicy`, a pure domain class with no dependencies, tested as a **property** over generated field combinations rather than a single example.

`localizedNames` is the interesting case and the reason `source` exists on that entity. Upstream already supplies 12 localised names (IA6), so the catalogue ships useful out of the box; a curator override is stored as a separate row with `source = CURATOR` and always wins at read time via the `displayName` resolution rule. Re-sync replaces `UPSTREAM` rows and never touches `CURATOR` rows.

State transitions encode the outcome: `STALE → SYNCED` when no proprietary fields are present, `STALE → CUSTOMIZED` when they are.

## Alternatives Considered

1. **Last-write-wins from upstream** — Trivial to implement: delete and re-insert. Rejected because it destroys the only irreplaceable data in the system, and it would do so silently on a background refresh.
2. **Block re-sync once a record is customised** — Safe for curator data. Rejected because it means a customised record can never receive an upstream correction, which defeats the point of replication and leaves the catalogue permanently divergent.
3. **Field-level "dirty" flags with three-way merge** — Track which fields the curator touched and merge only untouched ones. Rejected as over-engineering: it solves conflicts within a *shared* field set, but our field sets are disjoint, so the conflict it solves cannot arise. Choosing the simpler model was possible only because the partition was made explicit first.
4. **Store proprietary fields in a separate `PokemonAnnotation` table** — Physically guarantees the partition. Rejected as an extra join on every read for a guarantee the merge policy and a property test already provide; and a JSONB blob variant would make `region` and `tags` unqueryable and unvalidatable.

## Consequences

### Positive
- No data-loss path exists, and that claim is proven by a property test rather than asserted.
- Re-sync is total: it always succeeds or fails cleanly, never partially or ambiguously.
- The disjointness argument is a strong, compact thing to walk a panel through — it shows the conflict was *designed out* rather than handled.
- Upstream-seeded localised names make US03 demonstrable immediately instead of requiring hand-authored data.

### Negative
- Adding a new field requires an explicit decision about which set it belongs to, and getting that wrong is silent. Mitigated by making the partition a named constant in `PokemonMergePolicy` that the property test enumerates.
- `localizedNames` carries a `source` discriminator and can hold two rows for one locale, which the read path must resolve.

### Neutral
- Optimistic locking (`@Version`) remains necessary and orthogonal — it handles concurrent *curator* edits, whereas this ADR handles curator-versus-upstream.
- The `CUSTOMIZED` state is derived from proprietary-field presence, so it never needs to be set by hand.
