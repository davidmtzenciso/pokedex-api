# Replication State Machine

The lifecycle of a locally stored Pokémon. This is the most important diagram in the
repository.

```mermaid
stateDiagram-v2
    [*] --> PENDING: syncRequested(pokeApiId) - authenticated
    [*] --> DRAFT: manual POST /local - no pokeApiId supplied

    PENDING --> SYNCED: upstream 200 and mapping succeeded
    PENDING --> FAILED: upstream 4xx/5xx after 3 retries

    FAILED --> PENDING: retrySync - manual or scheduled
    FAILED --> [*]: DELETE - row removed

    DRAFT --> PENDING: linkToUpstream(pokeApiId) - id not already claimed
    DRAFT --> DRAFT: PATCH touches proprietary fields only
    DRAFT --> [*]: DELETE - row removed

    SYNCED --> CUSTOMIZED: PATCH/PUT writes any proprietary field
    SYNCED --> STALE: syncedAt + TTL is before now
    SYNCED --> [*]: DELETE - row removed

    CUSTOMIZED --> CUSTOMIZED: further proprietary edits
    CUSTOMIZED --> STALE: syncedAt + TTL is before now
    CUSTOMIZED --> [*]: DELETE - row removed

    STALE --> SYNCED: reSync succeeded and no proprietary fields present
    STALE --> CUSTOMIZED: reSync succeeded and proprietary fields preserved
    STALE --> FAILED: reSync exhausted retries

    %% Invalid: PENDING cannot go directly to CUSTOMIZED - must land SYNCED first
    %% Invalid: DRAFT cannot go directly to SYNCED - must pass through PENDING
    %% Invalid: SYNCED cannot return to PENDING - re-sync goes via STALE
    %% DELETE is terminal - the row is gone. Re-importing is a new record via PENDING
```

## Why this is a state machine and not a boolean

**Exactly one edge writes replicated fields from upstream**: `STALE → {SYNCED, CUSTOMIZED}`.
That edge runs through `PokemonMergePolicy`. There is no other path on which upstream data
can reach a persisted row.

An `isSynced` boolean would give you no such guarantee — you would have to audit every call
site to know whether curator data could be clobbered. Modelling the lifecycle explicitly
turns that audit into a single arrow.

`ReplicationState.transitionTo(next)` rejects any edge not on this diagram with
`IllegalStateTransitionException` → **409**.

## The two outcomes of a re-sync

| Guard | Result |
|---|---|
| No proprietary fields present | `STALE → SYNCED` — replicated fields overwritten wholesale |
| Proprietary fields present | `STALE → CUSTOMIZED` — replicated fields overwritten, **every proprietary field preserved byte-for-byte** |

The second is formal constraint **F7** and acceptance criterion **AC5**. It works because
`Proprietary ∩ Replicated = ∅` — the field sets are disjoint by construction, so there is
no conflict to resolve. See [ADR-0007](../adr/0007-proprietary-field-merge-policy.md).

## Invalid transitions, and why

| Forbidden | Reason |
|---|---|
| Any restore from a deleted record | `DELETE` removes the row — see [ADR-0010](../adr/0010-hard-deletes.md). Getting it back means re-syncing, which enters at `PENDING` as a genuinely new record with a new id |
| `PENDING → CUSTOMIZED` | You cannot customise what has not landed yet |
| `DRAFT → SYNCED` | A draft must be linked to an upstream id first, which is what `PENDING` represents |
| `SYNCED → PENDING` | Re-sync is a distinct operation with a distinct guard; it goes via `STALE` |

## Related

[Re-sync merge sequence](sequence-resync-merge.md) · [Re-sync error paths](error-paths-resync.md) · [ADR-0007](../adr/0007-proprietary-field-merge-policy.md)
