# Error Paths — Re-sync

Every branch of the most dangerous operation in the system.

```mermaid
flowchart TD
    A[reSync id requested] --> B{Record exists?}
    B -->|no| C([404 POKEMON_NOT_FOUND])
    B -->|yes| D{state in STALE, FAILED?}
    D -->|no| E([409 ILLEGAL_STATE_TRANSITION])
    D -->|yes| F[Fetch upstream]
    F --> G{Upstream reachable?}
    G -->|no| H[state' = FAILED]
    H --> I([502 UPSTREAM_UNAVAILABLE])
    G -->|yes| J{Proprietary fields<br/>non-empty?}
    J -->|no| K["Replicated' = upstream<br/>state' = SYNCED"]
    J -->|yes| L["Replicated' = upstream<br/>Proprietary' = existing (F7)<br/>state' = CUSTOMIZED"]
    K --> M{Optimistic lock held?}
    L --> M
    M -->|no| N([412 STALE_VERSION])
    M -->|yes| O([200 PokemonDetailDTO])
```

## What it encodes

- **The state guard comes before the network call.** Re-syncing a record that is not `STALE` or `FAILED` is rejected at 409 without spending an upstream request.
- **A failed fetch transitions to `FAILED`**, it does not leave the record in limbo. `FAILED` is retryable; an undefined state is not.
- **`J` is the only branch that matters for correctness**, and both arms write replicated fields. The difference is solely whether proprietary fields are carried across.
- **The optimistic-lock check is last**, so a concurrent curator edit during a slow upstream call surfaces as 412 rather than silently overwriting.

## Related

[Replication state machine](replication-state-machine.md) · [Batch sync error paths](error-paths-batch-sync.md)
